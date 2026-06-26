import AppKit
import CoreML
import Foundation

@MainActor
final class CaptchaCenter: ObservableObject {
    @Published var challenge: CaptchaChallenge?

    func requestAnswer(imageData: Data) async -> String? {
        await withCheckedContinuation { continuation in
            challenge = CaptchaChallenge(imageData: imageData) { [weak self] answer in
                Task { @MainActor in
                    self?.challenge = nil
                }
                continuation.resume(returning: answer)
            }
        }
    }
}

final class CaptchaChallenge: Identifiable, ObservableObject {
    let id = UUID()
    let imageData: Data
    private let completion: (String?) -> Void
    @Published var answer = ""

    init(imageData: Data, completion: @escaping (String?) -> Void) {
        self.imageData = imageData
        self.completion = completion
    }

    func submit() {
        completion(answer.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    func cancel() {
        completion(nil)
    }
}

@MainActor
final class CoreMLCaptchaSolver {
    private let charset: [Character] = [" ", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "+", "-", "*", "="]
    private let width = 130
    private let height = 42
    private lazy var model: MLModel? = loadModel()

    func solve(imageData: Data) async -> String? {
        guard let expression = predictExpression(imageData: imageData) else { return nil }
        return calculateCaptchaExpression(expression)
    }

    func predictExpression(imageData: Data) -> String? {
        guard let model,
              let input = makeInputTensor(imageData: imageData) else {
            return nil
        }

        do {
            let provider = try MLDictionaryFeatureProvider(dictionary: [
                "imageTensor": MLFeatureValue(multiArray: input)
            ])
            let output = try model.prediction(from: provider)
            guard let logits = output.featureValue(for: "logits")?.multiArrayValue else {
                return nil
            }
            return decodeLogits(logits)
        } catch {
            return nil
        }
    }

    private func loadModel() -> MLModel? {
        if let compiledURL = Bundle.main.url(forResource: "CaptchaCRNN", withExtension: "mlmodelc") {
            let configuration = MLModelConfiguration()
            configuration.computeUnits = .all
            return try? MLModel(contentsOf: compiledURL, configuration: configuration)
        }

        if let packageURL = Bundle.module.url(forResource: "CaptchaCRNN", withExtension: "mlpackage") {
            do {
                let compiledURL = try MLModel.compileModel(at: packageURL)
                let configuration = MLModelConfiguration()
                configuration.computeUnits = .all
                return try MLModel(contentsOf: compiledURL, configuration: configuration)
            } catch {
                return nil
            }
        }
        return nil
    }

    private func makeInputTensor(imageData: Data) -> MLMultiArray? {
        guard let cgImage = createCGImage(from: imageData) else { return nil }

        let totalPixels = width * height
        guard let tensor = try? MLMultiArray(shape: [1, 3, NSNumber(value: height), NSNumber(value: width)], dataType: .float32) else {
            return nil
        }

        let floatPtr = tensor.dataPointer.bindMemory(to: Float32.self, capacity: 3 * totalPixels)

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bytesPerPixel = 4
        let bytesPerRow = bytesPerPixel * width
        var pixelData = [UInt8](repeating: 0, count: bytesPerRow * height)

        guard let context = CGContext(
            data: &pixelData,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ) else {
            return nil
        }

        context.interpolationQuality = .high
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        for y in 0..<height {
            for x in 0..<width {
                let pixelOffset = y * bytesPerRow + x * bytesPerPixel
                let r = Float32(pixelData[pixelOffset]) / 255.0
                let g = Float32(pixelData[pixelOffset + 1]) / 255.0
                let b = Float32(pixelData[pixelOffset + 2]) / 255.0
                let tensorOffset = y * width + x
                floatPtr[tensorOffset] = r
                floatPtr[totalPixels + tensorOffset] = g
                floatPtr[2 * totalPixels + tensorOffset] = b
            }
        }

        return tensor
    }

    private func createCGImage(from data: Data) -> CGImage? {
        guard let nsImage = NSImage(data: data),
              let tiffData = nsImage.tiffRepresentation,
              let bitmap = NSBitmapImageRep(data: tiffData) else {
            return nil
        }
        return bitmap.cgImage
    }

    private func decodeLogits(_ logits: MLMultiArray) -> String {
        let shape = logits.shape.map { $0.intValue }
        let numClasses = shape.count > 2 ? shape[2] : (shape.count > 0 ? shape[0] : 15)
        let positions = shape.count > 0 ? shape[0] : 8
        let actualClasses = min(numClasses, charset.count)

        var indices: [Int] = []
        let strides = logits.strides.map { $0.intValue }

        for position in 0..<min(positions, 8) {
            var maxValue = -Float.greatestFiniteMagnitude
            var maxIndex = 0
            for classIndex in 0..<actualClasses {
                let offset: Int
                if shape.count == 3 {
                    offset = position * strides[0] + 0 * strides[1] + classIndex * strides[2]
                } else if shape.count == 2 {
                    offset = position * strides[0] + classIndex * strides[1]
                } else {
                    offset = position * actualClasses + classIndex
                }
                let value = floatAt(logits, offset: offset)
                if value > maxValue {
                    maxValue = value
                    maxIndex = classIndex
                }
            }
            if maxIndex >= charset.count {
                maxIndex = charset.count - 1
            }
            indices.append(maxIndex)
        }
        return decode(indices)
    }

    private func floatAt(_ array: MLMultiArray, offset: Int) -> Float {
        switch array.dataType {
        case .float32:
            return array.dataPointer.bindMemory(to: Float32.self, capacity: offset + 1)[offset]
        case .float16:
            return Float(array.dataPointer.bindMemory(to: Float16.self, capacity: offset + 1)[offset])
        case .double:
            return Float(array.dataPointer.bindMemory(to: Double.self, capacity: offset + 1)[offset])
        default:
            return Float(truncating: array[offset])
        }
    }

    private func decode(_ predictions: [Int]) -> String {
        var result = ""
        for (index, prediction) in predictions.enumerated() {
            let previous = index > 0 ? predictions[index - 1] : nil
            if prediction != previous, prediction != 0 {
                result.append(charset[prediction])
            }
        }
        return result
    }
}
