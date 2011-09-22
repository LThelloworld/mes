package com.qcadoo.mes.productionCounting.internal;

import static com.google.common.collect.Lists.newArrayList;
import static java.math.BigDecimal.ROUND_UP;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.productionCounting.internal.constants.ProductionCountingConstants;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.security.api.SecurityService;
import com.qcadoo.view.api.utils.NumberGeneratorService;

@Service
public class ProductionRecordService {

    @Autowired
    DataDefinitionService dataDefinitionService;

    @Autowired
    NumberGeneratorService numberGeneratorService;

    @Autowired
    SecurityService securityService;

    private final static String CUMULATE = "02cumulated";

    private final static String FOR_EACH_OPERATION = "03forEach";

    public void generateData(final DataDefinition dd, final Entity entity) {
        entity.setField("number", numberGeneratorService.generateNumber(ProductionCountingConstants.PLUGIN_IDENTIFIER, entity
                .getDataDefinition().getName()));
        entity.setField("creationTime", new Date());
        entity.setField("worker", securityService.getCurrentUserName());
    }

    public void checkTypeOfProductionRecording(final DataDefinition dd, final Entity entity) {
        Entity order = entity.getBelongsToField("order");
        String typeOfProductionRecording = order.getStringField("typeOfProductionRecording");
        if (typeOfProductionRecording == null || "01none".equals(typeOfProductionRecording)) {
            entity.addError(dd.getField("order"), "productionCounting.validate.global.error.productionRecord.orderError");
        }
    }

    public void allowedPartial(final DataDefinition dd, final Entity entity) {
        Entity order = entity.getBelongsToField("order");
        Boolean allowedPartial = (Boolean) order.getField("allowedPartial");
        Boolean isFinal = (Boolean) entity.getField("isFinal");
        if (!isFinal && allowedPartial) {
            entity.addError(dd.getField("order"),
                    "productionCounting.validate.global.error.productionRecord.orderError.allowedPartial");
        }
    }

    public void checkFinal(final DataDefinition dd, final Entity entity) {

    }

    public void copyProductInAndOut(final DataDefinition dd, final Entity productionRecord) {
        Entity order = productionRecord.getBelongsToField("order");
        String typeOfProductionRecording = order.getStringField("typeOfProductionRecording");
        List<Entity> operationComponents = null;

        for (String fieldName : newArrayList("recordOperationProductInComponents", "recordOperationProductOutComponents")) {
            if (productionRecord.getHasManyField(fieldName) != null) {
                return;
            }
        }

        if (CUMULATE.equals(typeOfProductionRecording)) {
            operationComponents = order.getTreeField("orderOperationComponents");
        } else if ("03forEach".equals(typeOfProductionRecording)) {
            operationComponents = newArrayList(productionRecord.getBelongsToField("orderOperationComponent"));
        }

        copyOperationProductComponents(operationComponents, productionRecord,
                ProductionCountingConstants.MODEL_RECORD_OPERATION_PRODUCT_IN_COMPONENT);
        copyOperationProductComponents(operationComponents, productionRecord,
                ProductionCountingConstants.MODEL_RECORD_OPERATION_PRODUCT_OUT_COMPONENT);
    }

    // TODO products list should be distinct?
    private void copyOperationProductComponents(final List<Entity> orderOperations, final Entity productionRecord,
            final String modelName) {
        if (orderOperations == null || orderOperations.size() == 0) {
            return;
        }

        DataDefinition recordProductDD = dataDefinitionService.get(ProductionCountingConstants.PLUGIN_IDENTIFIER, modelName);
        List<Entity> products = newArrayList();
        String technologyProductFieldName = "operationProductOutComponents";
        String recordProductFieldName = "recordOperationProductOutComponents";

        if (ProductionCountingConstants.MODEL_RECORD_OPERATION_PRODUCT_IN_COMPONENT.equals(modelName)) {
            technologyProductFieldName = "operationProductInComponents";
            recordProductFieldName = "recordOperationProductInComponents";
        }

        for (Entity orderOperation : orderOperations) {
            List<Entity> technologyProducts = orderOperation.getBelongsToField("technologyOperationComponent").getHasManyField(
                    technologyProductFieldName);
            if (technologyProducts == null) {
                continue;
            }

            for (Entity technologyProduct : technologyProducts) {
                Entity recordProduct = recordProductDD.create();
                recordProduct.setField("product", technologyProduct.getField("product"));
                recordProduct.setField("plannedQuantity", technologyProduct.getField("quantity"));
                products.add(recordProduct);
            }
        }
        productionRecord.setField(recordProductFieldName, products);
    }

    public void countPlannedTime(final DataDefinition dataDefinition, final Entity productionCounting) {
        Entity order = productionCounting.getBelongsToField("order");
        String typeOfProductionRecording = order.getStringField("typeOfProductionRecording");
        if (CUMULATE.equals(typeOfProductionRecording)) {
            List<Entity> operationComponents = order.getTreeField("orderOperationComponents");
            countPlannedTimeForCumulated(productionCounting, operationComponents);

        } else if (FOR_EACH_OPERATION.equals(typeOfProductionRecording)) {
            Entity orderOperationComponent = productionCounting.getBelongsToField("orderOperationComponent");
            countPlannedTimeForEachOperation(productionCounting, orderOperationComponent);
        }
    }

    private void countPlannedTimeForEachOperation(final Entity productionCounting, final Entity orderOperationComponent) {
        BigDecimal tpz = getBigDecimal(orderOperationComponent.getField("tpz"));
        BigDecimal tj = getBigDecimal(orderOperationComponent.getField("tj"));
        BigDecimal productionInOneCycle = getBigDecimal(orderOperationComponent.getField("productionInOneCycle"));
        BigDecimal machineUtilization = getBigDecimal(orderOperationComponent.getField("machineUtilization"));
        BigDecimal laborUtilization = getBigDecimal(orderOperationComponent.getField("laborUtilization"));
        BigDecimal plannedTime = (tj.multiply(productionInOneCycle)).add(tpz);
        BigDecimal plannedMachineTime = plannedTime.multiply(machineUtilization);
        BigDecimal plannedLaborTime = plannedTime.multiply(laborUtilization);

        productionCounting.setField("plannedTime", plannedTime.setScale(0, ROUND_UP).intValue());
        productionCounting.setField("plannedMachineTime", plannedMachineTime.setScale(0, ROUND_UP).intValue());
        productionCounting.setField("plannedLaborTime", plannedLaborTime.setScale(0, ROUND_UP).intValue());
    }

    private void countPlannedTimeForCumulated(final Entity productionCounting, final List<Entity> orderOperationComponents) {
        BigDecimal plannedTime = BigDecimal.ZERO;
        BigDecimal plannedMachineTime = BigDecimal.ZERO;
        BigDecimal plannedLaborTime = BigDecimal.ZERO;
        for (Entity orderOperationComponent : orderOperationComponents) {
            BigDecimal tpz = getBigDecimal(orderOperationComponent.getField("tpz"));
            BigDecimal tj = getBigDecimal(orderOperationComponent.getField("tj"));
            BigDecimal productionInOneCycle = getBigDecimal(orderOperationComponent.getField("productionInOneCycle"));
            BigDecimal machineUtilization = getBigDecimal(orderOperationComponent.getField("machineUtilization"));
            BigDecimal laborUtilization = getBigDecimal(orderOperationComponent.getField("laborUtilization"));
            plannedTime = (tj.multiply(productionInOneCycle)).add(tpz);
            plannedMachineTime = plannedTime.multiply(machineUtilization);
            plannedLaborTime = plannedTime.multiply(laborUtilization);
        }
        productionCounting.setField("plannedTime", plannedTime.setScale(0, ROUND_UP).intValue());
        productionCounting.setField("plannedMachineTime", plannedMachineTime.setScale(0, ROUND_UP).intValue());
        productionCounting.setField("plannedLaborTime", plannedLaborTime.setScale(0, ROUND_UP).intValue());
    }

    private BigDecimal getBigDecimal(final Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return BigDecimal.valueOf(Double.valueOf(value.toString()));
    }
}
