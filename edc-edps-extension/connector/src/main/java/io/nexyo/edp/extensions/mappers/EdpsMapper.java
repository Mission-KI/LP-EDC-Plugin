package io.nexyo.edp.extensions.mappers;

import io.nexyo.edp.extensions.dtos.EdpsDto;
import io.nexyo.edp.extensions.models.EdpsJobModel;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class EdpsMapper implements TypeTransformer<EdpsJobModel, EdpsDto> {

    @Override
    public Class<EdpsJobModel> getInputType() {
        return null;
    }

    @Override
    public Class<EdpsDto> getOutputType() {
        return null;
    }

    @Override
    public @Nullable EdpsDto transform(@NotNull EdpsJobModel edpModel, @NotNull TransformerContext transformerContext) {
        return null;
    }
}
