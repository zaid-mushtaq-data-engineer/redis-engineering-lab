package com.redislab.product.controller;

import com.redislab.common.response.PageResponse;
import com.redislab.common.util.PageRequestValidator;
import com.redislab.product.dto.AdminProductResponse;
import com.redislab.product.dto.CreateProductRequest;
import com.redislab.product.dto.ProductStatusResponse;
import com.redislab.product.dto.UpdateProductRequest;
import com.redislab.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@Validated
public class AdminProductController {

    private final ProductService productService;
    private final PageRequestValidator pageRequestValidator;

    public AdminProductController(ProductService productService, PageRequestValidator pageRequestValidator) {
        this.productService = productService;
        this.pageRequestValidator = pageRequestValidator;
    }

    @PostMapping
    public ResponseEntity<AdminProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        AdminProductResponse created = productService.createProduct(request);
        return ResponseEntity.created(URI.create("/api/admin/products/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public AdminProductResponse update(
            @PathVariable @Min(1) Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @PatchMapping("/{id}/activate")
    public ProductStatusResponse activate(@PathVariable @Min(1) Long id) {
        return productService.activateProduct(id);
    }

    @PatchMapping("/{id}/deactivate")
    public ProductStatusResponse deactivate(@PathVariable @Min(1) Long id) {
        return productService.deactivateProduct(id);
    }

    @GetMapping
    public PageResponse<AdminProductResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        int resolvedSize = size != null ? size : pageRequestValidator.defaultSize();
        return productService.listAdminProducts(page, resolvedSize);
    }

    @GetMapping("/{id}")
    public AdminProductResponse get(@PathVariable @Min(1) Long id) {
        return productService.getAdminProduct(id);
    }
}
