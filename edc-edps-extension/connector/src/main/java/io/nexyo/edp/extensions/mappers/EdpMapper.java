package io.nexyo.edp.extensions.mappers;

import io.nexyo.edp.extensions.dtos.EdpDto;
import io.nexyo.edp.extensions.models.EdpJobModel;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class EdpMapper implements TypeTransformer<EdpJobModel, EdpDto> {

    @Override
    public Class<EdpJobModel> getInputType() {
        return null;
    }

    @Override
    public Class<EdpDto> getOutputType() {
        return null;
    }

    @Override
    public @Nullable EdpDto transform(@NotNull EdpJobModel edpModel, @NotNull TransformerContext transformerContext) {
        return null;
    }
}
