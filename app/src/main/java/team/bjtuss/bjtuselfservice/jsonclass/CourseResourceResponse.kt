package team.bjtuss.bjtuselfservice.jsonclass

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.squareup.moshi.Json
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonTransformingSerializer

data class CourseResourceResponse(
    val STATUS: String,
    val bagList: List<Bag>?,
    val resList: List<Res>?,
)

