package team.bjtuss.bjtuselfservice.web;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import team.bjtuss.bjtuselfservice.utils.Network.WebCallback;

/** The SSO handshake only; MIS data and AA login continue using the existing client. */
final class MisSsoLogin {
    static final String LOGIN_URL = "https://mis.bjtu.edu.cn/auth/nsso/?next=/home/";
    static final int NETWORK_ERROR = 0;
    static final int LOGIN_REJECTED = 1;
    static final int VERIFICATION_REQUIRED = 2;
    static final int PROTOCOL_ERROR = 3;
    private static final String TAG = "MisSsoLogin";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";

    private MisSsoLogin() {}

    static void login(OkHttpClient client, String username, String password,
                      WebCallback<String> callback) {
        // Share the existing cookie jar, but never follow an HTTPS-to-HTTP redirect.
        OkHttpClient loginClient = client.newBuilder().followSslRedirects(false).build();
        Request entry = new Request.Builder().url(LOGIN_URL)
                .header("User-Agent", USER_AGENT).build();
        loginClient.newCall(entry).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                fail(callback, NETWORK_ERROR);
            }

            @Override
            public void onResponse(Call call, Response response) {
                String studentInfo = null;
                Request submission = null;
                int errorCode = -1;
                try (Response ignored = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        errorCode = response.code() >= 500 ? NETWORK_ERROR : PROTOCOL_ERROR;
                    } else {
                        HttpUrl pageUrl = response.request().url();
                        String html = response.body().string();
                        if (isMisHome(pageUrl)) {
                            studentInfo = parseStudentInfo(html);
                        } else {
                            submission = createLoginRequest(pageUrl, html, username, password);
                        }
                    }
                } catch (LoginPageException e) {
                    errorCode = e.code;
                } catch (IOException e) {
                    errorCode = NETWORK_ERROR;
                } catch (RuntimeException e) {
                    errorCode = PROTOCOL_ERROR;
                }

                if (errorCode >= 0) {
                    fail(callback, errorCode);
                } else if (studentInfo != null) {
                    callback.onResponse(studentInfo);
                } else if (submission != null) {
                    submit(loginClient, submission, callback);
                } else {
                    fail(callback, PROTOCOL_ERROR);
                }
            }
        });
    }

    private static void submit(OkHttpClient client, Request request, WebCallback<String> callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                fail(callback, NETWORK_ERROR);
            }

            @Override
            public void onResponse(Call call, Response response) {
                String studentInfo = null;
                int errorCode = -1;
                try (Response ignored = response) {
                    if (response.body() == null || response.code() >= 500) {
                        errorCode = response.code() >= 500 ? NETWORK_ERROR : PROTOCOL_ERROR;
                    } else {
                        HttpUrl finalUrl = response.request().url();
                        String html = response.body().string();
                        if (response.isSuccessful() && isMisHome(finalUrl)) {
                            studentInfo = parseStudentInfo(html);
                        } else if (isSsoUrl(finalUrl)) {
                            errorCode = loginFailureCode(html);
                        } else {
                            errorCode = PROTOCOL_ERROR;
                        }
                    }
                } catch (LoginPageException e) {
                    errorCode = e.code;
                } catch (IOException e) {
                    errorCode = NETWORK_ERROR;
                } catch (RuntimeException e) {
                    errorCode = PROTOCOL_ERROR;
                }

                if (studentInfo != null) {
                    callback.onResponse(studentInfo);
                } else {
                    fail(callback, errorCode >= 0 ? errorCode : PROTOCOL_ERROR);
                }
            }
        });
    }

    private static void fail(WebCallback<String> callback, int code) {
        // Do not log account details, page HTML, cookies or OAuth callback URLs.
        String reason = code == VERIFICATION_REQUIRED ? "Additional verification required"
                : code == PROTOCOL_ERROR ? "Unexpected SSO page or response"
                : code == LOGIN_REJECTED ? "SSO login rejected"
                : "SSO network request failed";
        Log.w(TAG, reason);
        callback.onFailure(code);
    }

    static Request createLoginRequest(HttpUrl pageUrl, String html, String username,
                                      String password) throws LoginPageException {
        if (!isSsoUrl(pageUrl)) {
            throw new LoginPageException(PROTOCOL_ERROR);
        }
        Document doc = Jsoup.parse(html);
        String scripts = scriptText(doc);
        if (loginFailureCode(scripts) == VERIFICATION_REQUIRED) {
            throw new LoginPageException(VERIFICATION_REQUIRED);
        }
        Element form = doc.selectFirst("form#loginForm");
        if (form == null || !"post".equalsIgnoreCase(form.attr("method"))) {
            throw new LoginPageException(PROTOCOL_ERROR);
        }
        HttpUrl action = pageUrl.resolve(form.attr("action"));
        if (action == null || !isSsoUrl(action) || !"/sso/login".equals(action.encodedPath())) {
            throw new LoginPageException(PROTOCOL_ERROR);
        }

        Map<String, String> fields = new LinkedHashMap<>();
        for (Element input : form.select("input[name]")) {
            String type = input.attr("type");
            if (input.hasAttr("disabled") || "reset".equalsIgnoreCase(type)
                    || "button".equalsIgnoreCase(type) || "file".equalsIgnoreCase(type)) {
                continue;
            }
            if (("checkbox".equalsIgnoreCase(type) || "radio".equalsIgnoreCase(type))
                    && !input.hasAttr("checked")) {
                continue;
            }
            fields.put(input.attr("name"), input.attr("value"));
        }
        HttpUrl callback = HttpUrl.parse(fields.getOrDefault("redirect_uri", ""));
        if (fields.getOrDefault("flowId", "").isEmpty()
                || fields.getOrDefault("client_id", "").isEmpty()
                || fields.getOrDefault("state", "").isEmpty()
                || !"code".equals(fields.get("response_type"))
                || callback == null || !callback.isHttps() || callback.port() != 443
                || !"mis.bjtu.edu.cn".equals(callback.host())
                || !"/auth/ncallback/".equals(callback.encodedPath())) {
            throw new LoginPageException(PROTOCOL_ERROR);
        }
        JsonObject config = objectVariable(scripts, "ssoConfig");
        if (config == null || !config.has("sm2") || !config.get("sm2").isJsonObject()) {
            throw new LoginPageException(PROTOCOL_ERROR);
        }
        JsonObject sm2 = config.getAsJsonObject("sm2");
        if (!sm2.has("enabled") || !sm2.get("enabled").isJsonPrimitive()
                || !sm2.getAsJsonPrimitive("enabled").isBoolean()) {
            throw new LoginPageException(PROTOCOL_ERROR);
        }
        if (sm2.get("enabled").getAsBoolean()
                && (!sm2.has("publicKey") || !sm2.get("publicKey").isJsonPrimitive()
                || !sm2.getAsJsonPrimitive("publicKey").isString())) {
            throw new LoginPageException(PROTOCOL_ERROR);
        }
        fields.put("username", username);
        fields.put("password", sm2.get("enabled").getAsBoolean()
                ? encryptPassword(password, sm2.get("publicKey").getAsString()) : password);
        fields.put("loginType", "username_password");
        FormBody.Builder body = new FormBody.Builder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.add(field.getKey(), field.getValue());
        }
        return new Request.Builder().url(action).post(body.build())
                .header("Referer", pageUrl.toString())
                .header("Origin", "https://sso.bjtu.edu.cn")
                .header("User-Agent", USER_AGENT).build();
    }

    static String encryptPassword(String password, String publicKey) throws LoginPageException {
        try {
            X9ECParameters curve = GMNamedCurves.getByName("sm2p256v1");
            ECDomainParameters domain = new ECDomainParameters(
                    curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
            ECPublicKeyParameters key = new ECPublicKeyParameters(
                    curve.getCurve().decodePoint(Base64.getDecoder().decode(publicKey)), domain);
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(true, new ParametersWithRandom(key, new SecureRandom()));
            byte[] plaintext = password.getBytes(StandardCharsets.UTF_8);
            try {
                byte[] ciphertext = engine.processBlock(plaintext, 0, plaintext.length);
                // The official sm2.js uses C1C3C2 without the uncompressed-point prefix 04.
                return Base64.getEncoder().encodeToString(Arrays.copyOfRange(ciphertext, 1, ciphertext.length));
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (Exception e) {
            throw new LoginPageException(PROTOCOL_ERROR);
        }
    }

    static boolean isMisHome(HttpUrl url) {
        return url.isHttps() && url.port() == 443 && "mis.bjtu.edu.cn".equals(url.host())
                && ("/home/".equals(url.encodedPath()) || "/home".equals(url.encodedPath()));
    }

    private static boolean isSsoUrl(HttpUrl url) {
        return url.isHttps() && url.port() == 443 && "sso.bjtu.edu.cn".equals(url.host())
                && url.username().isEmpty() && url.password().isEmpty();
    }

    static String parseStudentInfo(String html) throws LoginPageException {
        Document doc = Jsoup.parse(html);
        Element name = doc.selectFirst(".name_right > h3 > a");
        Element identity = doc.selectFirst(".name_right .nr_con span:contains(身份)");
        Element department = doc.selectFirst(".name_right .nr_con span:contains(部门)");
        if (name == null || identity == null || department == null || name.text().isEmpty()) {
            throw new LoginPageException(PROTOCOL_ERROR);
        }
        return name.text().split("，")[0] + ";" + identity.text().replace("身份：", "")
                + ";" + department.text().replace("部门：", "");
    }

    static int loginFailureCode(String htmlOrScript) {
        String scripts = htmlOrScript.contains("<script")
                ? scriptText(Jsoup.parse(htmlOrScript)) : htmlOrScript;
        JsonObject error = objectVariable(scripts, "error");
        if (error != null) {
            if (error.has("data") && error.get("data").isJsonObject()
                    && error.getAsJsonObject("data").has("captchaType")) {
                return VERIFICATION_REQUIRED;
            }
            if (error.has("code")) {
                int code = error.get("code").getAsInt();
                if (code == 41100 || code == 41102) {
                    return VERIFICATION_REQUIRED;
                }
            }
        }
        Matcher page = Pattern.compile("\\bvar\\s+pageName\\s*=\\s*[\"']([^\"']+)[\"']")
                .matcher(scripts);
        if (page.find() && !"login".equals(page.group(1))) {
            return VERIFICATION_REQUIRED;
        }
        return LOGIN_REJECTED;
    }

    private static String scriptText(Document doc) {
        StringBuilder text = new StringBuilder();
        for (Element script : doc.select("script:not([src])")) {
            text.append(script.data()).append('\n');
        }
        return text.toString();
    }

    private static JsonObject objectVariable(String scripts, String name) {
        Matcher match = Pattern.compile("\\bvar\\s+" + Pattern.quote(name)
                + "\\s*=\\s*(\\{.*?\\}|null)\\s*;", Pattern.DOTALL).matcher(scripts);
        if (!match.find()) {
            return null;
        }
        JsonElement value = JsonParser.parseString(match.group(1));
        return value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    static final class LoginPageException extends IOException {
        final int code;

        LoginPageException(int code) {
            super("SSO login failure code " + code);
            this.code = code;
        }
    }
}
