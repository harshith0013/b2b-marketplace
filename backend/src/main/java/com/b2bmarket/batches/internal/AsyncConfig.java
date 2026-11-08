package com.b2bmarket.batches.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Background work (reading label photos after upload) on Spring Boot's task executor. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {}
