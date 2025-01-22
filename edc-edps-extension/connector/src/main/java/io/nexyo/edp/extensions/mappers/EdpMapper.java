package io.nexyo.edp.extensions.mappers;

import io.nexyo.edp.extensions.dtos.EdpDto;
import io.nexyo.edp.extensions.models.EdpModel;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class EdpMapper implements TypeTransformer<EdpModel, EdpDto> {

    @Override
    public Class<EdpModel> getInputType() {
        return null;
    }

    @Override
    public Class<EdpDto> getOutputType() {
        return null;
    }

    @Override
    public @Nullable EdpDto transform(@NotNull EdpModel edpModel, @NotNull TransformerContext transformerContext) {
        return null;
    }
}
