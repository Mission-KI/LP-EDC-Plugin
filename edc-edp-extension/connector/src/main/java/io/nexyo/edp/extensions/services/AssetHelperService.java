package io.nexyo.edp.extensions.services;

import io.nexyo.edp.extensions.dtos.internal.EdpsJobDto;
import io.nexyo.edp.extensions.utils.LoggingUtils;
import org.eclipse.edc.connector.controlplane.services.spi.asset.AssetService;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Optional;

/**
 * Service for handling assets and storing job information.
 */
public class AssetHelperService {

    private static final String EDPS_JOB_ID_KEY = "edps_job_id";
    private final AssetService assetService;
    private final Monitor logger;

    /**
     * Constructor for the AssetHelperService.
     * 
     * @param assetService the asset service
     */ 
    public AssetHelperService(AssetService assetService) {
        this.assetService = assetService;
        this.logger = LoggingUtils.getLogger();
    }


    /**
     * Persists the job information on the asset.
     * 
     * @param assetId the asset id
     * @param edpsJobDto the job information
     */
    public void persistJobInfo(String assetId, EdpsJobDto edpsJobDto) {
        var asset = this.assetService.findById(assetId);
        var updatedAsset = asset.toBuilder().property(EDPS_JOB_ID_KEY, edpsJobDto.getJobId())
                .build();
        var result = assetService.update(updatedAsset);
        if (result.failed()) {
            this.logger.warning("Could not store job information on asset: " + assetId);
        }
    }


    /**
     * Retrieves the job id from the asset.
     * 
     * @param assetId the asset id
     * @return the job id
     */ 
    public Optional<String> getEdpsJobId(String assetId) {
        var asset = this.assetService.findById(assetId);
        var jobId = asset.getProperty(EDPS_JOB_ID_KEY);

        if (jobId == null) {
            return Optional.empty();
        }

        return Optional.of(jobId.toString());
    }

}
