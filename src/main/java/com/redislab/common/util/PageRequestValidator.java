package com.redislab.common.util;

import com.redislab.common.exception.InvalidPaginationException;
import com.redislab.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
public class PageRequestValidator {

    private final AppProperties appProperties;

    public PageRequestValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void validate(int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException();
        }
        int maxSize = appProperties.pagination().maxSize();
        if (size < 1 || size > maxSize) {
            throw new InvalidPaginationException();
        }
    }

    public int defaultPage() {
        return 0;
    }

    public int defaultSize() {
        return appProperties.pagination().defaultSize();
    }
}
