package com.propenine.siku.controllerstok;

import com.propenine.siku.model.User;
import com.propenine.siku.service.AuthenticationService;
import com.propenine.siku.service.katalog.KategoriService;
import com.propenine.siku.servicestok.ProductService;
import org.springframework.util.StringUtils;
import jakarta.validation.Valid;

import com.propenine.siku.dtostok.request.CreateProductRequestDTO;
import com.propenine.siku.dtostok.request.UpdateProductRequestDTO;
import com.propenine.siku.modelstok.Product;
import com.propenine.siku.dtostok.ProductMapper;
import com.propenine.siku.repositorystok.ProductDb;
import java.util.UUID;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

//import org.hibernate.validator.constraints.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ProductController {
    @Autowired
    KategoriService kategoriService;

    @Autowired
    private ResourceLoader resourceLoader;

    // Autentikasi
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    @GetMapping("stok")
    public String listProduct(Model model) {
        var listProduct = productService.getAllProduct();
        model.addAttribute("listProduct", listProduct);

        // Autentikasi
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        return "viewall-product";
    }

    @GetMapping("product/tambah")
    public String formAddProduct(Model model) {
        System.out.println("ke sini");
        var productDTO = new CreateProductRequestDTO();
        var allKategori = kategoriService.getAllKategori();
        model.addAttribute("allKategori", allKategori);
        model.addAttribute("productDTO", productDTO);

        // Autentikasi
        User loggedInUser = authenticationService.getLoggedInUser();
        System.out.println(loggedInUser.toString());
        System.out.println(loggedInUser.getRole());
        model.addAttribute("user", loggedInUser);

        return "form-tambah-product";
    }

    @PostMapping("/product/tambah")
    public String addProduct(@Valid @ModelAttribute("productDTO") CreateProductRequestDTO productDTO,
            @RequestParam("imageFile") MultipartFile imageFile,
            BindingResult bindingResult, Model model) {

        if (imageFile.isEmpty()) {
            return "Image file is empty";
        }
        try {
            // Tentukan lokasi absolut direktori resource static images
            String uploadDir = resourceLoader.getResource("classpath:static/images/").getFile().getAbsolutePath();
            System.out.println(uploadDir);
            System.out.println(uploadDir);
            System.out.println(productDTO.getKategori());
            // Salin file gambar ke dalam direktori penyimpanan
            byte[] bytes = imageFile.getBytes();
            String name = System.currentTimeMillis() + imageFile.getOriginalFilename();
            Path imagePath = Paths.get(uploadDir + "/" + name);

            if (productDTO.getStok() > 0) {
                productDTO.setStatus(true);
            } else {
                productDTO.setStatus(false);
            }
            productDTO.setImage(name);
            Product product = productMapper.createProductRequestDTOToProduct(productDTO);
            productService.createProduct(product);
            model.addAttribute("productAdded", true);
            Files.write(imagePath, bytes);
            System.out.println(imagePath);
            System.out.println("berhasil menambahkan product");
            User loggedInUser = authenticationService.getLoggedInUser();
            model.addAttribute("user", loggedInUser);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "form-tambah-product";
    }

    @GetMapping("product/update/{idProduct}")
    public String formUpdateProduct(@PathVariable("idProduct") UUID idProduct, Model model) {
        var product = productService.getProductById(idProduct);
        var productDTO = productMapper.productToUpdateProductRequestDTO(product);
        var allKategori = kategoriService.getAllKategori();

        model.addAttribute("productDTO", productDTO);
        model.addAttribute("allKategori", allKategori);

        // Autentikasi
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        return "form-update-product";
    }

    @PostMapping("product/update/{idProduct}")
    public String updateProductPost(@PathVariable("idProduct") UUID idProduct,
            @Valid @ModelAttribute("productDTO") UpdateProductRequestDTO productDTO, Model model,
            @RequestParam(required = false) MultipartFile imageFile) {
        if (productDTO.getStok() > 0) {
            productDTO.setStatus(true);
        } else {
            productDTO.setStatus(false);
        }
        String uploadDir;
        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                uploadDir = resourceLoader.getResource("classpath:static/images/").getFile().getAbsolutePath();
                byte[] bytes = imageFile.getBytes();
                String name = System.currentTimeMillis() + imageFile.getOriginalFilename();
                Path imagePath = Paths.get(uploadDir + "/" + name);

                Files.write(imagePath, bytes);
                System.out.println(imagePath);
                System.out.println("berhasil menambahkan product");
                productDTO.setImage(name);
            } else {
                // If no image file is provided, retain the existing image
                Product existingProduct = productService.getProductById(idProduct);
                productDTO.setImage(existingProduct.getImage());
            }

            var productFromDto = productMapper.updateProductRequestDTOToProduct(productDTO);
            var product = productService.updateProduct(productFromDto);
            model.addAttribute("idProduct", product.getIdProduct());
            model.addAttribute("productUpdated", true);
            System.out.println("success update product");
            // Autentikasi
            User loggedInUser = authenticationService.getLoggedInUser();
            model.addAttribute("user", loggedInUser);
        } catch (IOException e) {
            // Handle IOException
            e.printStackTrace();
        }

        return "form-update-product";
    }
}
