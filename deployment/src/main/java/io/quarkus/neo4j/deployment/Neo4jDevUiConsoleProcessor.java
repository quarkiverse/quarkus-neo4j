package io.quarkus.neo4j.deployment;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.neo4j.runtime.devui.Neo4jDevUIJsonRpcService;

class Neo4jDevUiConsoleProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem create() {
        var cardPageBuildItem = new CardPageBuildItem();
        cardPageBuildItem.addPage(Page.externalPageBuilder("Neo4j Browser")
                .icon("font-awesome-solid:diagram-project")
                .dynamicUrlJsonRPCMethodName("getBrowserUrl")
                .doNotEmbed());
        return cardPageBuildItem;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem createJsonRPCService() {
        return new JsonRPCProvidersBuildItem(Neo4jDevUIJsonRpcService.class);
    }
}
