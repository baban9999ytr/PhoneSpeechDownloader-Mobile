package com.example.callhistory;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Url;

public interface ApiService {
    @POST
    Call<ResponseBody> uploadCallLogJson(
            @Url String uploadUrl,
            @Header("x-auth-token") String authToken,
            @Body CallUploadPayload payload
    );

    @Multipart
    @POST
    Call<ResponseBody> uploadCallLogMultipart(
            @Url String uploadUrl,
            @Header("x-auth-token") String authToken,
            @Part MultipartBody.Part mediaFile,
            @Part("json_data") RequestBody jsonData
    );
}
