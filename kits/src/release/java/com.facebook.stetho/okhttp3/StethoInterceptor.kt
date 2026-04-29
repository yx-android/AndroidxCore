package com.facebook.stetho.okhttp3

import okhttp3.Interceptor
import okhttp3.Response

class StethoInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}