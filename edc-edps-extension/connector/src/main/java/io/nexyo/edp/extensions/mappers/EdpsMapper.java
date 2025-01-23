package io.nexyo.edp.extensions.mappers;

import io.nexyo.edp.extensions.dtos.internal.EdpsJobDto;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class EdpsMapper implements TypeTransformer<EdpsJobDto, io.nexyo.edp.extensions.dtos.external.EdpsJobResponseDto> {

    @Override
    public Class<EdpsJobDto> getInputType() {
        return null;
    }

    @Override
    public Class<io.nexyo.edp.extensions.dtos.external.EdpsJobResponseDto> getOutputType() {
        return null;
    }

    @Override
    public @Nullable io.nexyo.edp.extensions.dtos.external.EdpsJobResponseDto transform(@NotNull EdpsJobDto edpModel, @NotNull TransformerContext transformerContext) {
        return transformerContext.transform(edpModel, io.nexyo.edp.extensions.dtos.external.EdpsJobResponseDto.class);
    }
}
