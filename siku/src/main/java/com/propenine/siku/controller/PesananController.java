package com.propenine.siku.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.propenine.siku.model.Klien;
import com.propenine.siku.model.Pesanan;
import com.propenine.siku.model.RekapAgent;
import com.propenine.siku.model.RekapKlien;
import com.propenine.siku.model.RekapPenjualan;
import com.propenine.siku.model.User;
import com.propenine.siku.modelstok.Product;
import com.propenine.siku.repository.PesananRepository;
import com.propenine.siku.service.AuthenticationService;
import com.propenine.siku.service.PesananService;
import com.propenine.siku.service.UserServiceImpl;
import com.propenine.siku.service.klien.KlienService;
import com.propenine.siku.servicestok.ProductService;

@Controller
@RequestMapping("/pesanan")
public class PesananController {

    private final ProductService productService;
    private final PesananService pesananService;
    private final KlienService klienService;
    private final UserServiceImpl userServiceImpl;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    public PesananController(PesananService pesananService, ProductService productService, KlienService klienService,
            UserServiceImpl userServiceImpl) {
        this.pesananService = pesananService;
        this.productService = productService;
        this.klienService = klienService;
        this.userServiceImpl = userServiceImpl;

    }

    @GetMapping("/")
    public String root() {
        return "redirect:/pesanan/list";
    }


    @GetMapping("/list")
    public String listAllPesanan(
            @RequestParam(name = "searchInput", required = false) String searchInput,
            @RequestParam(name = "statusPesanan", required = false) String statusPesanan,
            @RequestParam(name = "tanggalPemesanan", required = false) String tanggalPemesanan,
            Model model) {

        List<Pesanan> pesananList;

        if ((searchInput != null && !searchInput.isEmpty()) || (statusPesanan != null
                && !statusPesanan.isEmpty())
                || (tanggalPemesanan != null && !tanggalPemesanan.isEmpty())) {
            pesananList = pesananService.findWithFilters(searchInput, statusPesanan,
                    tanggalPemesanan);
        } else {
            pesananList = pesananService.getAllPesanan();
        }

        pesananList.forEach(pesanan -> {
            int totalCost = pesanan.getProduct().getHarga() * pesanan.getJumlahBarangPesanan();
            pesanan.setJumlahBiayaPesanan(totalCost);
        });
        

        pesananList.sort(Comparator.comparing(Pesanan::getStatusPesanan,
                Comparator.comparing(status -> {
                    switch (status) {
                        case "Ongoing":
                            return 0;
                        case "Complete":
                            return 1;
                        case "Canceled":
                            return 2;
                        default:
                            return 3;
                    }
                })));

        model.addAttribute("pesananList", pesananList);

        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        return "pesanan/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);
        List<Product> productList = productService.getAllProduct();
        List<Klien> klienList = klienService.getAllKlien();
        List<User> userList = userServiceImpl.getAllUsers();
        model.addAttribute("pesanan", new Pesanan());
        model.addAttribute("productList", productList);
        model.addAttribute("klienList", klienList);
        model.addAttribute("userList", userList);

        return "pesanan/create";
    }

    @PostMapping("/create")
    public String createPesanan(@ModelAttribute Pesanan pesanan, RedirectAttributes redirectAttributes, Model model) {
        Product product = pesanan.getProduct();
        Klien klien = pesanan.getKlien();
        User user = pesanan.getUser();
        int jumlahBarangPesanan = pesanan.getJumlahBarangPesanan();

        if (user != null && klien != null && product != null && jumlahBarangPesanan > 0) {
            int stokSaatIni = product.getStok();

            if (stokSaatIni >= jumlahBarangPesanan) {
                product.setStok(stokSaatIni - jumlahBarangPesanan);
                if (product.getStok() > 0) {
                    product.setStatus(true);
                } else {
                    product.setStatus(false);
                }
                pesanan.setJumlahBiayaPesanan(jumlahBarangPesanan * product.getHarga());

                pesananService.createPesanan(pesanan);

                return "redirect:/pesanan/list";
            } else {
                redirectAttributes.addFlashAttribute("warningMessage", "Insufficient stock.");
            }
        } else {
            redirectAttributes.addFlashAttribute("warningMessage", "Please complete all order data.");
        }
        model.addAttribute("orderAdded", true);

        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);


        return "redirect:/pesanan/create";
    }

    @GetMapping("/update/{id}")
    public String showUpdateForm(@PathVariable Long id, Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);
        List<Product> productList = productService.getAllProduct();
        List<Klien> klienList = klienService.getAllKlien();
        List<User> userList = userServiceImpl.getAllUsers();

        model.addAttribute("userList", userList);
        model.addAttribute("klienList", klienList);
        model.addAttribute("productList", productList);
        Pesanan pesanan = pesananService.getPesananById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid pesanan Id:" + id));
        model.addAttribute("pesanan", pesanan);
        model.addAttribute("tanggalPemesanan", pesanan.getTanggalPemesanan().toString());
        model.addAttribute("tanggalSelesai", pesanan.getTanggalSelesai().toString());

        return "pesanan/update";
    }


    @PostMapping("/update/{id}")
    public String updatePesanan(@PathVariable Long id, @ModelAttribute Pesanan updatedPesanan,
            RedirectAttributes redirectAttributes) {
        Pesanan existingPesanan = pesananService.getPesananById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid pesanan Id:" + id));
        Product product = updatedPesanan.getProduct();
        Klien klien = updatedPesanan.getKlien();
        User user = updatedPesanan.getUser();
        int jumlahBarangPesanan = updatedPesanan.getJumlahBarangPesanan();
        int jumlahBarangPesananSebelumnya = existingPesanan.getJumlahBarangPesanan();
        int selisihJumlahPesanan = jumlahBarangPesananSebelumnya - jumlahBarangPesanan;
        int hasilAkhir = product.getStok() + selisihJumlahPesanan;

        if (updatedPesanan.getTanggalPemesanan() == null || updatedPesanan.getTanggalSelesai() == null) {
            updatedPesanan.setTanggalPemesanan(existingPesanan.getTanggalPemesanan());
            updatedPesanan.setTanggalSelesai(existingPesanan.getTanggalSelesai());
        }

        if (hasilAkhir >= 0) {
            if (user != null && klien != null && product != null && jumlahBarangPesanan > 0) {
                product.setStok(product.getStok() + selisihJumlahPesanan);
                productService.updateProduct(product);
            } else {
                redirectAttributes.addFlashAttribute("warningMessage", "The number of items cannot be 0.");
                return "redirect:/pesanan/update/" + id;
            }
        } else {
            redirectAttributes.addFlashAttribute("warningMessage", "Insufficient Stock.");
            return "redirect:/pesanan/update/" + id;
        }

        pesananService.updatePesanan(id, updatedPesanan);
        redirectAttributes.addFlashAttribute("orderUpdated", true);
        return "redirect:/pesanan/update/" + id;

    }

    @GetMapping("/delete/{id}")
    public String deletePesanan(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            pesananService.deletePesanan(id);
            redirectAttributes.addFlashAttribute("successMessage", "Pesanan has been deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error occurred while deleting the pesanan.");
        }
        redirectAttributes.addFlashAttribute("successMessage", "Client deleted successfully.");
        return "redirect:/pesanan/list";
    }
    @GetMapping("/rekap-penjualan")
    public String getOrderSummary(
            @RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) Integer tahun,
            @RequestParam(required = false) String namaProduct,
            @RequestParam(required = false) String kategori,
            Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        if ((bulan == null && tahun != null) || (bulan != null && tahun == null)) {
            model.addAttribute("message", "Fill out both the month and year to see recap.");
            return "laporan-penjualan";
        }

        List<RekapPenjualan> orderSummary;

        if (bulan != null && tahun != null) {
            if (namaProduct != null && !namaProduct.isEmpty() && kategori != null && !kategori.isEmpty()) {
                orderSummary = pesananService.getOrderSummaryByMonthAndYear(bulan, tahun);
                orderSummary = orderSummary.stream()
                        .filter(rekap_penjualan -> rekap_penjualan.getProduct().getNamaProduct().toLowerCase().contains(namaProduct.toLowerCase()) &&
                                rekap_penjualan.getProduct().getKategori().getNamaKategori().toLowerCase().contains(kategori.toLowerCase()))
                        .collect(Collectors.toList());
            } else if (namaProduct != null && !namaProduct.isEmpty()) {
                orderSummary = pesananService.getOrderSummaryByMonthAndYear(bulan, tahun);
                orderSummary = orderSummary.stream()
                        .filter(rekap_penjualan -> rekap_penjualan.getProduct().getNamaProduct().toLowerCase().contains(namaProduct.toLowerCase()))
                        .collect(Collectors.toList());
            } else if (kategori != null && !kategori.isEmpty()) {
                orderSummary = pesananService.getOrderSummaryByMonthAndYear(bulan, tahun);
                orderSummary = orderSummary.stream()
                        .filter(rekap_penjualan -> rekap_penjualan.getProduct().getKategori().getNamaKategori().toLowerCase().contains(kategori.toLowerCase()))
                        .collect(Collectors.toList());
            } else {
                orderSummary = pesananService.getOrderSummaryByMonthAndYear(bulan, tahun);
            }
        } else {
            if (namaProduct != null && !namaProduct.isEmpty() && kategori != null && !kategori.isEmpty()) {
                orderSummary = pesananService.getAllOrderSummaries();
                orderSummary = orderSummary.stream()
                        .filter(rekap_penjualan -> rekap_penjualan.getProduct().getNamaProduct().toLowerCase().contains(namaProduct.toLowerCase()) &&
                                rekap_penjualan.getProduct().getKategori().getNamaKategori().toLowerCase().contains(kategori.toLowerCase()))
                        .collect(Collectors.toList());
            } else if (namaProduct != null && !namaProduct.isEmpty()) {
                orderSummary = pesananService.getAllOrderSummaries();
                orderSummary = orderSummary.stream()
                        .filter(rekap_penjualan -> rekap_penjualan.getProduct().getNamaProduct().toLowerCase().contains(namaProduct.toLowerCase()))
                        .collect(Collectors.toList());
            } else if (kategori != null && !kategori.isEmpty()) {
                orderSummary = pesananService.getAllOrderSummaries();
                orderSummary = orderSummary.stream()
                        .filter(rekap_penjualan -> rekap_penjualan.getProduct().getKategori().getNamaKategori().toLowerCase().contains(kategori.toLowerCase()))
                        .collect(Collectors.toList());
            } else {
                orderSummary = pesananService.getAllOrderSummaries();
            }
        }

        orderSummary.sort(Comparator.comparing(RekapPenjualan::getTotalQuantity).reversed());

        model.addAttribute("orderSummary", orderSummary);
        return "laporan-penjualan";

    }

    @GetMapping("/rekap-klien")
    public String getOrderSummaryByKlien(
            @RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) Integer tahun,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) String sortBy,
            Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        List<RekapKlien> klienSummary;

        if (bulan != null && tahun != null) {
            if (clientName != null && !clientName.isEmpty()) {
                klienSummary = pesananService.getKlienSummaryByMonthAndYear(bulan, tahun);
                klienSummary = klienSummary.stream().filter(rekap_klien -> rekap_klien.getKlien().getNamaKlien().toLowerCase().contains(clientName))
                        .collect(Collectors.toList());
            } else {
                klienSummary = pesananService.getKlienSummaryByMonthAndYear(bulan, tahun);
            }
        } else {
            if (clientName != null && !clientName.isEmpty()) {
                klienSummary = pesananService.getAllKlienSummaries();
                klienSummary = klienSummary.stream().filter(rekap_klien -> rekap_klien.getKlien().getNamaKlien().toLowerCase().contains(clientName.toLowerCase()))
                    .collect(Collectors.toList());
            } else {
                klienSummary = pesananService.getAllKlienSummaries();
            }
        }

        if (sortBy == null || sortBy.equals("mostItems")) {          
            klienSummary.sort(Comparator.comparing(RekapKlien::getTotalQuantity).reversed());
        } else if (sortBy.equals("leastItems")) {
            klienSummary.sort(Comparator.comparing(RekapKlien::getTotalQuantity));
        } else if (sortBy.equals("highestPrice")) {
            klienSummary.sort(Comparator.comparing(RekapKlien::getTotalPrice).reversed());
        } else if (sortBy.equals("lowestPrice")) {
            klienSummary.sort(Comparator.comparing(RekapKlien::getTotalPrice));
        }

        if ((bulan == null && tahun != null) || (bulan != null && tahun == null)) {
            model.addAttribute("message", "Fill out both the month and year to see recap.");
            return "laporan-klien";
        } else if (klienSummary.isEmpty()) {
            model.addAttribute("message", "No orders found.");
            return "laporan-klien";
        } else {
            model.addAttribute("klienSummary", klienSummary);
            return "laporan-klien";
        }
    }

    @GetMapping("/rekap-agent")
    public String getOrderSummaryByAgent(
            @RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) Integer tahun,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String sortBy,
            Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        List<RekapAgent> agentSummary;

        if (bulan != null && tahun != null) {
            if (agentName != null && !agentName.isEmpty()) {
                agentSummary = pesananService.getAgentSummaryByMonthAndYear(bulan, tahun);
                agentSummary = agentSummary.stream()
                        .filter(rekap_agent -> (rekap_agent.getUser().getNama_depan().toLowerCase() + " " + rekap_agent.getUser().getNama_belakang().toLowerCase())
                        .contains(agentName.toLowerCase()))
                        .collect(Collectors.toList());
            } else {
                agentSummary = pesananService.getAgentSummaryByMonthAndYear(bulan, tahun);
            }
        } else {
            if (agentName != null && !agentName.isEmpty()) {
                agentSummary = pesananService.getAllAgentSummaries();
                agentSummary = agentSummary.stream()
                        .filter(rekap_agent -> (rekap_agent.getUser().getNama_depan().toLowerCase() + " " + rekap_agent.getUser().getNama_belakang().toLowerCase())
                        .contains(agentName.toLowerCase()))
                        .collect(Collectors.toList());
            } else {
                agentSummary = pesananService.getAllAgentSummaries();
            }
        }

        if (sortBy == null || sortBy.equals("mostItems")) {          
            agentSummary.sort(Comparator.comparing(RekapAgent::getTotalQuantity).reversed());
        } else if (sortBy.equals("leastItems")) {
            agentSummary.sort(Comparator.comparing(RekapAgent::getTotalQuantity));
        } else if (sortBy.equals("highestPrice")) {
            agentSummary.sort(Comparator.comparing(RekapAgent::getTotalPrice).reversed());
        } else if (sortBy.equals("lowestPrice")) {
            agentSummary.sort(Comparator.comparing(RekapAgent::getTotalPrice));
        }

        if ((bulan == null && tahun != null) || (bulan != null && tahun == null)) {
            model.addAttribute("message", "Fill out both the month and year to see recap.");
            return "laporan-agent";
        } else if (agentSummary.isEmpty()) {
            model.addAttribute("message", "No orders found.");
            return "laporan-agent";
        } else {
            model.addAttribute("agentSummary", agentSummary);
            return "laporan-agent";
        }
    }

    @GetMapping("/rekap-penjualan-chart")
    public String getOrderSummaryChart(@RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) Integer tahun,
            Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        // Get the current month and year
        LocalDate currentDate = LocalDate.now();
        int currentMonth = currentDate.getMonthValue();
        int currentYear = currentDate.getYear();

        // Fetch order summary data based on the provided month and year or use the
        // current month and year by default
        List<RekapPenjualan> orderSummary;
        if (bulan != null && tahun != null) {
            orderSummary = pesananService.getOrderSummaryByMonthAndYear(bulan, tahun);
        } else {
            orderSummary = pesananService.getOrderSummaryByMonthAndYear(currentMonth, currentYear);
        }

        orderSummary.sort(Comparator.comparing(RekapPenjualan::getTotalQuantity).reversed());

        List<RekapPenjualan> topProducts = orderSummary.stream()
                .limit(10)
                .collect(Collectors.toList());

        // Pass the order summary data and current month/year to the Thymeleaf template
        model.addAttribute("orderSummary", topProducts);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("currentYear", currentYear);

        return "rekap-penjualan-chart";
    }

    // UNTUK DASHBOARD
    @GetMapping("/dashboard")
    public String getOrderSummaryChartDashboard(@RequestParam(required = false) Integer bulan,
                                       @RequestParam(required = false) Integer tahun,
                                       Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        // Get the current month and year
        LocalDate currentDate = LocalDate.now();
        int currentMonth = currentDate.getMonthValue();
        int currentYear = currentDate.getYear();

        // Fetch order summary data based on the provided month and year or use the current month and year by default
        List<RekapPenjualan> orderSummary;
        List<RekapKlien> klienSummary;
        List<RekapAgent> agentSummary;

        if (bulan != null && tahun != null) {
            orderSummary = pesananService.getOrderSummaryByMonthAndYear(bulan, tahun);
            klienSummary = pesananService.getKlienSummaryByMonthAndYear(bulan, tahun);
            agentSummary = pesananService.getAgentSummaryByMonthAndYear(bulan, tahun);
        } else {
            orderSummary = pesananService.getOrderSummaryByMonthAndYear(currentMonth, currentYear);
            klienSummary = pesananService.getKlienSummaryByMonthAndYear(currentMonth, currentYear);
            agentSummary = pesananService.getAgentSummaryByMonthAndYear(currentMonth, currentYear);
        }

        orderSummary.sort(Comparator.comparing(RekapPenjualan::getTotalQuantity).reversed());
        klienSummary.sort(Comparator.comparing(RekapKlien::getTotalQuantity).reversed());
        agentSummary.sort(Comparator.comparing(RekapAgent::getTotalQuantity).reversed());

        List<RekapPenjualan> topProducts = orderSummary.stream()
                .limit(10)
                .collect(Collectors.toList());
        List<RekapKlien> topClients = klienSummary.stream()
                .limit(10)
                .collect(Collectors.toList());
        List<RekapAgent> topAgents = agentSummary.stream()
                .limit(10)
                .collect(Collectors.toList());

        // Pass the order summary data and current month/year to the Thymeleaf template
        model.addAttribute("orderSummary", topProducts);
        model.addAttribute("klienSummary", topClients);
        model.addAttribute("agentSummary", topAgents);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("currentYear", currentYear);

        return "dashboard";
    }

    @GetMapping("/rekap-klien-chart")
    public String getKlienSummaryChart(@RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) Integer tahun,
            Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        LocalDate currentDate = LocalDate.now();
        int currentMonth = currentDate.getMonthValue();
        int currentYear = currentDate.getYear();

        List<RekapKlien> klienSummary;
        if (bulan != null && tahun != null) {
            klienSummary = pesananService.getKlienSummaryByMonthAndYear(bulan, tahun);
        } else {
            klienSummary = pesananService.getKlienSummaryByMonthAndYear(currentMonth, currentYear);
        }

        klienSummary.sort(Comparator.comparing(RekapKlien::getTotalQuantity).reversed());

        List<RekapKlien> topClients = klienSummary.stream()
                .limit(10)
                .collect(Collectors.toList());

        model.addAttribute("klienSummary", topClients);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("currentYear", currentYear);

        return "rekap-klien-chart";
    }

    @GetMapping("/rekap-agent-chart")
    public String getAgentSummaryChart(@RequestParam(required = false) Integer bulan,
            @RequestParam(required = false) Integer tahun,
            Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        LocalDate currentDate = LocalDate.now();
        int currentMonth = currentDate.getMonthValue();
        int currentYear = currentDate.getYear();

        List<RekapAgent> agentSummary;
        if (bulan != null && tahun != null) {
            agentSummary = pesananService.getAgentSummaryByMonthAndYear(bulan, tahun);
        } else {
            agentSummary = pesananService.getAgentSummaryByMonthAndYear(currentMonth, currentYear);
        }

        agentSummary.sort(Comparator.comparing(RekapAgent::getTotalQuantity).reversed());

        List<RekapAgent> topAgents = agentSummary.stream()
                .limit(10)
                .collect(Collectors.toList());

        // Pass the order summary data and current month/year to the Thymeleaf template
        model.addAttribute("agentSummary", topAgents);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("currentYear", currentYear);

        // Return the name of the Thymeleaf template to render
        return "rekap-agent-chart";
    }

    @GetMapping("/rekap-penjualan-chart-data")
    @ResponseBody
    public List<RekapPenjualan> getOrderSummaryChartData(@RequestParam int bulan, @RequestParam int tahun,
            Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        // Fetch order summary data for the specified month and year
        List<RekapPenjualan> orderSummary = pesananService.getOrderSummaryByMonthAndYear(bulan, tahun);
        return orderSummary;
    }

    // UNTUK DASHBOARD
    @GetMapping("/rekap-penjualan-chart-data-dashboard")
    @ResponseBody
    public List<RekapPenjualan> getOrderSummaryChartDataDashboard(@RequestParam int bulan, @RequestParam int tahun, Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        // Fetch order summary data for the specified month and year
        List<RekapPenjualan> orderSummary = pesananService.getOrderSummaryByMonthAndYear(bulan, tahun);
        return orderSummary;
    }

    @GetMapping("/rekap-klien-chart-data-dashboard")
    @ResponseBody
    public List<RekapKlien> getKlienSummaryChartDataDashboard(@RequestParam int bulan, @RequestParam int tahun, Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        // Fetch order summary data for the specified month and year
        List<RekapKlien> klienSummary = pesananService.getKlienSummaryByMonthAndYear(bulan, tahun);
        return klienSummary;
    }

    @GetMapping("/rekap-klien-chart-data")
    @ResponseBody
    public List<RekapKlien> getKlienSummaryChartData(@RequestParam int bulan, @RequestParam int tahun, Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        // Fetch order summary data for the specified month and year
        List<RekapKlien> klienSummary = pesananService.getKlienSummaryByMonthAndYear(bulan, tahun);
        return klienSummary;
    }

    @GetMapping("/rekap-agent-chart-data")
    @ResponseBody
    public List<RekapAgent> getAgentSummaryChartData(@RequestParam int bulan, @RequestParam int tahun, Model model) {
        User loggedInUser = authenticationService.getLoggedInUser();
        model.addAttribute("user", loggedInUser);

        // Fetch order summary data for the specified month and year
        List<RekapAgent> agentSummary = pesananService.getAgentSummaryByMonthAndYear(bulan, tahun);
        return agentSummary;
    }
}
