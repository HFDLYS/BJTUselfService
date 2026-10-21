package team.bjtuss.bjtuselfservice.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.Test;

public class MisSsoLoginTest {
    @Test
    public void encryptsUtf8PasswordsInSchoolWireFormat() throws Exception {
        X9ECParameters curve = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domain = new ECDomainParameters(
                curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(domain, new SecureRandom()));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(
                ((ECPublicKeyParameters) pair.getPublic()).getQ().getEncoded(false));
        for (String password : new String[]{"test-password", "临时密码123"}) {
            byte[] wire = Base64.getDecoder().decode(MisSsoLogin.encryptPassword(password, publicKey));
            assertEquals(96 + password.getBytes(StandardCharsets.UTF_8).length, wire.length);
            byte[] ciphertext = new byte[wire.length + 1];
            ciphertext[0] = 4;
            System.arraycopy(wire, 0, ciphertext, 1, wire.length);
            SM2Engine decryptor = new SM2Engine(SM2Engine.Mode.C1C3C2);
            decryptor.init(false, pair.getPrivate());
            assertEquals(password, new String(decryptor.processBlock(ciphertext, 0, ciphertext.length),
                    StandardCharsets.UTF_8));
        }
    }

    @Test
    public void submitsDynamicOauthFieldsToValidatedSsoEndpoint() throws Exception {
        Request request = MisSsoLogin.createLoginRequest(
                HttpUrl.get("https://sso.bjtu.edu.cn/o/authorize/"), loginPage("/sso/login"),
                "test-account", "test-password");
        FormBody form = (FormBody) request.body();
        assertEquals("https://sso.bjtu.edu.cn/sso/login", request.url().toString());
        assertEquals("test-account", field(form, "username"));
        assertEquals("flow-test", field(form, "flowId"));
        assertEquals("state-test", field(form, "state"));
        assertEquals("username_password", field(form, "loginType"));
        assertFalse(field(form, "password").isEmpty());
        assertFalse(field(form, "password").equals("test-password"));
    }

    @Test
    public void rejectsCredentialSubmissionOutsideSso() throws Exception {
        try {
            MisSsoLogin.createLoginRequest(HttpUrl.get("https://sso.bjtu.edu.cn/o/authorize/"),
                    loginPage("https://example.com/sso/login"), "test-account", "test-password");
            fail("External form target must be rejected");
        } catch (MisSsoLogin.LoginPageException expected) {
            assertEquals(MisSsoLogin.PROTOCOL_ERROR, expected.code);
        }
    }

    private static String field(FormBody form, String name) {
        for (int i = 0; i < form.size(); i++) {
            if (name.equals(form.name(i))) return form.value(i);
        }
        fail("Missing form field: " + name);
        return "";
    }

    private static String loginPage(String action) {
        return "<script>var ssoConfig = {\"sm2\":{\"enabled\":true,\"publicKey\":"
                + "\"BI4oS9hOB37bD49HNCmIz3ij4QUU4AROOLDjDYTlEJu7uQf0O5BPIm0k9Iktf+JhwO/nEZLXZQpUY7vtnC5FmpA=\"}};</script>"
                + "<form id='loginForm' method='post' action='" + action + "'>"
                + "<input name='flowId' value='flow-test'><input name='client_id' value='client-test'>"
                + "<input name='redirect_uri' value='https://mis.bjtu.edu.cn/auth/ncallback/'>"
                + "<input name='response_type' value='code'><input name='state' value='state-test'>"
                + "<input name='username'><input name='password'></form>";
    }
}
