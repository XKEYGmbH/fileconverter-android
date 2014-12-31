package at.xkey.fileconverter.client;

import com.google.gson.annotations.Expose;
import com.squareup.okhttp.OkHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.OkClient;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.http.Streaming;
import retrofit.mime.TypedFile;

public class FileConverterClient {
    private static final String API_URL = "https://autoocr.may.co.at:8001/AutoOCRService";

    static class Contributor {
        String login;
        int contributions;
    }

    public interface FileConverter {
        @GET("/Auth")
        Map<String,AuthResult> Auth();
        static class AuthResult {
            String AuthResult;
        }

        @GET("/GetStatus")
        GetStatusResult GetStatus(
            @Query("jobID") String jobID
        );
        public class GetStatusResult {
            @Expose
            public Integer GetStatusResult;
        }

        @GET("/GetJob")
        GetJobResult GetJob(
            @Query("jobID") String jobID
        );
        public class GetJobResult {
            @Expose
            public Job GetJobResult;
        }
        public class Job {
            @Expose
            public String ConversionStartedDateISO;
            @Expose
            public String FinishedDateISO;
            @Expose
            public String JobGuid;
            @Expose
            public String JobLabel;
            @Expose
            public Integer PageCount;
            @Expose
            public Integer Status;
        }

        @GET("/GetResultCount")
        GetResultCountResult GetResultCount(
                @Query("jobID") String jobID
        );
        public class GetResultCountResult {
            @Expose
            public Integer GetResultCountResult;
        }

        @GET("/GetResultExt")
        GetResultExtResult GetResultExt(
                @Query("jobID") String jobID,
                @Query("index") Integer index
        );
        public class GetResultExtResult {
            @Expose
            public String GetResultExtResult;
        }



        @GET("/GetSettingsCollection")
        GetSettingsCollectionResult GetSettingsCollection();
        public class GetSettingsCollectionResult {
            @Expose
            public List<SettingCollection> GetSettingsCollectionResult;
        }

        public class SettingCollection {
            @Expose
            public List<String> AdditionalOutput;
            @Expose
            public String Description;
            @Expose
            public String EngineName;
            @Expose
            public List<String> InputFormats;
            @Expose
            public List<String> InputFormatsMT;
            @Expose
            public List<String> OutputFormatsMT;
            @Expose
            public String SettingsName;
        }

        @GET("/GetResultEx")
        @Streaming
        void GetResultEx(
                @Query("jobID") String jobID,
                @Query("index") Integer index,
                Callback<Response> callback
        );

        @GET("/GetResultEx")
        @Streaming
        Response GetResultEx(
                @Query("jobID") String jobID,
                @Query("index") Integer index
        );

        @PUT("/UploadJobEx")
        UploadJobExResult UploadJob(
                @Query("ext") String ext,
                @Query("settingsName") String settingsName,
                @Query("label") String label,
                @Body TypedFile file
        );

        public class UploadJobExResult {
            @Expose
            public Job UploadJobExResult;
        }

        public class FileResult {
            public byte[] bytes;
            public String filename;
            public String ext;
        }
    }

    private static FileConverter mInstance = null;

    private FileConverterClient(){

    }

    public static FileConverter getInstance(){
        if(mInstance == null)
        {

            TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
            };

            OkHttpClient okHttpClient = new OkHttpClient();
            SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, null);
            } catch (GeneralSecurityException e) {
                throw new AssertionError(); // The system has no TLS. Just give up.
            }
            okHttpClient.setSslSocketFactory(sslContext.getSocketFactory());

            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            okHttpClient.setHostnameVerifier(allHostsValid);

            FileConverterRequestInterceptor requestInterceptor = new FileConverterRequestInterceptor();
            requestInterceptor.setUser("admin");
            requestInterceptor.setPassword("autoocr");
            // Create a very simple REST adapter which points the GitHub API endpoint.
            return new RestAdapter.Builder()
                    .setRequestInterceptor(requestInterceptor)
                    .setClient(new OkClient(okHttpClient))
                    .setEndpoint(API_URL)
                    .build()
                    .create(FileConverter.class);
        }
        return mInstance;
    }

    public static void saveBytesToFile(byte[] bytes, String path) {
        try {
            FileOutputStream fileOuputStream = new FileOutputStream(path);
            fileOuputStream.write(bytes);
            fileOuputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static byte[] getBytesFromStream(InputStream is) throws IOException {

        int len;
        int size = 1024;
        byte[] buf;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        buf = new byte[size];
        while((len = is.read(buf, 0, size)) != -1) {
            bos.write(buf, 0, len);
        }
        buf = bos.toByteArray();

        return buf;
    }

    public static void UploadAndWaitFor(final TypedFile file, final Callback<FileConverter.FileResult> callback) {
        try {
            FileConverter.UploadJobExResult result = FileConverterClient.getInstance().UploadJob(FileConverterClient.getExtension(file.file().getName()), "default", "label", file);

            String jobID = result.UploadJobExResult.JobGuid;
            Integer status = result.UploadJobExResult.Status;
            final String plainFilename = file.fileName().substring(0, file.fileName().lastIndexOf('.'));

            while (status < 4) {
                FileConverter.GetStatusResult statusResult = FileConverterClient.getInstance().GetStatus(jobID);
                status = statusResult.GetStatusResult;
            }

            if (status == 4) {
                FileConverter.GetResultExtResult extResult = FileConverterClient.getInstance().GetResultExt(jobID,0);
                final String ext = extResult.GetResultExtResult;

                FileConverterClient.getInstance().GetResultEx(jobID, 0, new Callback<Response>() {
                    @Override
                    public void success(Response response, Response response2) {
                        try {
                            byte[] bytes = FileConverterClient.getBytesFromStream(response.getBody().in());
                            FileConverter.FileResult fileResult = new FileConverter.FileResult();
                            fileResult.ext = ext;
                            fileResult.filename = plainFilename + "." + ext;
                            fileResult.bytes = bytes;
                            callback.success(fileResult,response);
                        }
                        catch (Exception e) {
                            callback.failure(RetrofitError.unexpectedError("", e));
                        }
                    }

                    @Override
                    public void failure(RetrofitError error) {

                    }
                });


            }
            //callback.success(result, null);
        }
        catch (Exception e) {
            callback.failure(RetrofitError.unexpectedError("", e));
        }
    }

    public static String getExtension(String fileName) {
        String extension = "";

        int i = fileName.lastIndexOf('.');
        int p = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));

        if (i > p) {
            extension = fileName.substring(i+1);
        }
        return extension;
    }
}