package com.cssd.trace.config;

import jakarta.servlet.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebStaticResourceConfig implements WebMvcConfigurer {

    // 开发阶段禁止浏览器缓存静态资源，避免 Web 页面更新后 Chrome 继续使用旧 CSS/JS。
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore().mustRevalidate().cachePrivate().maxAge(0, TimeUnit.SECONDS));
    }

    // 对所有 Web 响应增加强制禁用缓存头，解决 Chrome 已打开页面继续显示旧样式的问题。
    @Bean
    public FilterRegistrationBean<Filter> noStoreHeaderFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter((request, response, chain) -> {
            var httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
            chain.doFilter(request, response);
            httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setDateHeader("Expires", 0);
        });
        bean.addUrlPatterns("/*");
        bean.setOrder(1);
        return bean;
    }
}
