package com.redislab.product.controller;

import com.redislab.common.response.PageResponse;
import com.redislab.common.util.PageRequestValidator;
import com.redislab.product.dto.ProductResponse;
import com.redislab.product.service.ProductService;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductService productService;
    private final PageRequestValidator pageRequestValidator;

    public ProductController(ProductService productService, PageRequestValidator pageRequestValidator) {
        this.productService = productService;
        this.pageRequestValidator = pageRequestValidator;
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable @Min(1) Long id) {
        return productService.getPublicProduct(id);
    }

    @GetMapping
    public PageResponse<ProductResponse> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        int resolvedSize = size != null ? size : pageRequestValidator.defaultSize();
        return productService.listPublicProducts(page, resolvedSize);
    }
}
