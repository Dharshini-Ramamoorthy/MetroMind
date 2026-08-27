package com.kce.kmrl.fleet.service;

import com.kce.kmrl.fleet.model.AuditLedgerEntry;
import com.kce.kmrl.fleet.model.TrainAsset;
import com.kce.kmrl.fleet.model.TrainStatus;
import com.kce.kmrl.fleet.repository.AuditLedgerRepository;
import com.kce.kmrl.fleet.repository.TrainAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class DataInitializerService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializerService.class);

    private final TrainAssetRepository trainRepository;
    private final AuditLedgerRepository ledgerRepository;

    @Value("${fleet.seed.force:false}")
    private boolean forceReseed;

    public DataInitializerService(TrainAssetRepository trainRepository, AuditLedgerRepository ledgerRepository) {
        this.trainRepository = trainRepository;
        this.ledgerRepository = ledgerRepository;
    }

    private static final String[] RIVER_NAMES = {
        "Periyar", "Pamba", "Kabani", "Bhavani", "Chaliyar", "Bharathapuzha", "Meenachil",
        "Kaveri", "Muvattupuzha", "Chalakkudy", "Achankovil", "Manimala", "Pamba-II",
        "Neyyar", "Kallada", "Valapattanam", "Karatoya", "Gayathri", "Siruvani",
        "Korapuzha", "Irikkur", "Thanikkudam", "Kuthiran", "Pennar", "Chaliyar-II"
    };

    @Override
    public void run(String... args) {
        long existing = trainRepository.count();
        if (existing > 0 && !forceReseed) {
            log.info("Fleet database has {} trainset(s) — skipping seed.", existing);
            return;
        }

        log.info("Initializing Fleet Microservice Database with 25 KMRL Trainsets...");

        try {
            trainRepository.deleteAll();
            ledgerRepository.deleteAll();
        } catch (Exception e) {
            log.warn("Could not clear Fleet collections: {}", e.getMessage());
        }

        for (int i = 1; i <= 25; i++) {
            String padId = String.format("%02d", i);
            String id = "TS-" + padId;
            String name = "KMRL Set " + padId + " (" + RIVER_NAMES[i - 1] + ")";

            TrainStatus status = TrainStatus.STANDBY;
            String depot = "Muttom Depot Yard";
            String track = "Yard Staging Bay " + ((i - 1) / 3 + 1) + "-Track " + ((i - 1) % 3 + 1);
            int health = 91 + (i % 8);
            int brakePressure = 890 + (i * 2);

            TrainAsset train = new TrainAsset(
                id, name, "Alstom Metropolis 3-Car Rake", status,
                depot, track, null, null, health,
                "2026-08-01", brakePressure, 100000 + (i * 1250)
            );
            train.setMileageAtLastServiceKm(train.getTotalMileageKm());
            trainRepository.save(train);
        }

        List<AuditLedgerEntry> ledger = Arrays.asList(
            new AuditLedgerEntry("L-905", "2026-08-05 17:40", "KMRL Set 07 (Meenachil)", "Yard Bay 3 -> Mainline Block G", "OP-4401 (Priya)", "0x9a8f...3102"),
            new AuditLedgerEntry("L-904", "2026-08-05 16:15", "KMRL Set 23 (Kuthiran)", "Yard Bay 9 -> Workshop Bay 1", "OP-1102 (Rajesh)", "0x4b12...99d1")
        );
        ledgerRepository.saveAll(ledger);

        log.info("Fleet Database Initialization Complete! Seeded 25 Trainsets on Standby.");
    }
}