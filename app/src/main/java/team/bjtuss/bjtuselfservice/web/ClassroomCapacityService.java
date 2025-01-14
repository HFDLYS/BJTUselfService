package team.bjtuss.bjtuselfservice.web;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import team.bjtuss.bjtuselfservice.constant.ApiConstant;

import org.json.JSONArray;
import org.json.JSONObject;

public class ClassroomCapacityService {
    public static CompletableFuture<BuildingInfo> getClassroomCapacity(String BuildingName) {
        CompletableFuture<BuildingInfo> future = new CompletableFuture<>();
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(ApiConstant.CLASSROOM_CAPACITY_URL + BuildingName)
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseBody);
                        List<ClassroomCapacity> classroomList = new ArrayList<>();
                        JSONArray timeArray = jsonObject.getJSONArray("time");
                        String effectiveDateStart = timeArray.getString(0);
                        String effectiveDateEnd = timeArray.getString(1);
                        JSONArray dataArray = jsonObject.getJSONArray("data");
                        for (int i = 0; i < dataArray.length(); i++) {
                            JSONArray classroom = dataArray.getJSONArray(i);
                            String roomName = classroom.getString(0);
                            int capacity = classroom.getInt(3);
                            int used = classroom.getInt(2);
                            classroomList.add(new ClassroomCapacity(roomName, capacity, used));
                        }
                        future.complete(new BuildingInfo(BuildingName, classroomList, effectiveDateStart, effectiveDateEnd));
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                } else {
                    future.completeExceptionally(new Exception("Response not successful"));
                }
            }

            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static class ClassroomCapacity {
        public String RoomName;
        public int Capacity;
        public int Used;

        public ClassroomCapacity(String RoomName, int Capacity, int Used) {
            this.RoomName = RoomName;
            this.Capacity = Capacity;
            this.Used = Used;
        }
    }

    public static class BuildingInfo {
        public String BuildingName;
        public List<ClassroomCapacity> ClassroomList;
        public String EffectiveDateStart;
        public String EffectiveDateEnd;

        BuildingInfo(String BuildingName, List<ClassroomCapacity> ClassroomList, String EffectiveDateStart, String EffectiveDateEnd) {
            this.BuildingName = BuildingName;
            this.ClassroomList = ClassroomList;
            this.EffectiveDateStart = EffectiveDateStart;
            this.EffectiveDateEnd = EffectiveDateEnd;
        }
    }
}
