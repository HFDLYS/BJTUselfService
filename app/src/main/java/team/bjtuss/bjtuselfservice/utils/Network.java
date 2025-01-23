package team.bjtuss.bjtuselfservice.utils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class Network {
    public interface WebCallback<T> {
        void onResponse(T obj);

        void onFailure(int errcode);
    }

    public static class InMemoryCookieJar implements CookieJar {
        private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

        @Override
        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
            List<Cookie> oldCookies = cookieStore.get(url.host());
            if (oldCookies != null) {
                HashMap<String, Cookie> newCookies = new HashMap<>();
                for (Cookie cookie : oldCookies) {
                    newCookies.put(cookie.name(), cookie);
                }
                for (Cookie cookie : cookies) {
                    newCookies.put(cookie.name(), cookie);
                }
                cookieStore.put(url.host(), new ArrayList<>(newCookies.values()));
            } else {
                cookieStore.put(url.host(), cookies);
            }
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            List<Cookie> cookies = cookieStore.get(url.host());
            return cookies != null ? cookies : new ArrayList<Cookie>();
        }

    }
}
