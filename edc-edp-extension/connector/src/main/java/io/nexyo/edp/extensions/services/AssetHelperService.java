package io.nexyo.edp.extensions.services;

import io.nexyo.edp.extensions.utils.LoggingUtils;
import org.eclipse.edc.connector.controlplane.services.spi.asset.AssetService;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Optional;

/**
 * Service for handling assets and storing job information.
 */
public class AssetHelperService {

    public static final String EDPS_JOB_ID_KEY = "edps_job_id";
    public static final String DASEEN_RESOURCE_ID_KEY = "daseen_resource_id";
    private final AssetService assetService;
    private final Monitor logger;

    /**
     * Constructor for the AssetHelperService.
     *
     *
     * @param assetService the asset service
     */ 
    public AssetHelperService(AssetService assetService) {
        this.assetService = assetService;
        this.logger = LoggingUtils.getLogger();
    }


    /**
     * Persists information on the asset.
     *
     *
     * @param assetId the asset id
     * @param key the key to store the data under
     * @param data the data to store on the asset
     */
    public void persist(String assetId, String key, String data) {
        var asset = this.assetService.findById(assetId);
        var updatedAsset = asset.toBuilder().property(key, data)
                .build();
        var result = assetService.update(updatedAsset);
        if (result.failed()) {
            this.logger.warning("Could not store job information on asset: " + assetId);
        }
    }


    /**
     * Retrieves stored information on the asset.
     *
     *
     * @param assetId the asset id
     * @param key the key to retrieve the data from
     * @return the stored information
     */ 
    public Optional<String> load(String assetId, String key) {
        var asset = this.assetService.findById(assetId);
        var jobId = asset.getProperty(key);

        if (jobId == null) {
            return Optional.empty();
        }

        return Optional.of(jobId.toString());
    }

}
