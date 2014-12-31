package at.xkey.fileconverter.client;


import org.ow2.util.base64.Base64;

import java.net.PasswordAuthentication;

import retrofit.RequestInterceptor;

/**
 * Interceptor used to authorize requests.
 */
public class FileConverterRequestInterceptor implements RequestInterceptor {

    private String user;
    private String password;

    @Override
    public void intercept(RequestFacade requestFacade) {
        if (user != null) {
            final String authorizationValue = encodeCredentialsForBasicAuthorization();
            requestFacade.addHeader("Authorization", authorizationValue);
        }
    }

    private String encodeCredentialsForBasicAuthorization() {
        String authString = user + ":" + password;
        System.out.println("auth string: " + authString);
        String authStringEnc = base64Encode(authString);
        return "Basic " +authStringEnc;
    }

    public static String base64Encode(String token) {
        char[] encodedBytes = Base64.encode(token.getBytes());
        return new String(encodedBytes);
    }
    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}