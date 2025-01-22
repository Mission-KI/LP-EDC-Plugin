package io.nexyo.edp.extensions.services;


import io.nexyo.edp.extensions.models.EdpJobModel;
import org.eclipse.edc.spi.monitor.Monitor;

public class EdpsService {

    private final Monitor logger;
    // api client goes here

    public EdpsService(Monitor monitor) {
        this.logger = monitor;
    }

    public EdpJobModel createEdpsJob(String assetId) {
        this.logger.info(String.format("Creating EDP job for %s...", assetId));

        return new EdpJobModel();
    }

}
