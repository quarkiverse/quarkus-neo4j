package io.quarkus.neo4j.deployment;

import java.util.List;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

class Neo4jDevUiConsoleProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem create(
            List<DevServicesResultBuildItem> runningDevServices,
            Neo4jBuildTimeConfig neo4jBuildTimeConfig) {

        var cardPageBuildItem = new CardPageBuildItem();
        if (Neo4jDevServicesProcessor.enabled(neo4jBuildTimeConfig.devservices())) {

            // Find the appropriate config
            for (DevServicesResultBuildItem runningDevService : runningDevServices) {
                if (runningDevService.getConfig().containsKey(Neo4jDevServicesProcessor.NEO4J_BROWSER_URL)) {
                    cardPageBuildItem.addPage(Page.externalPageBuilder("Neo4J Browser")
                            .icon("font-awesome-solid:diagram-project")
                            .url(runningDevService.getConfig().get(Neo4jDevServicesProcessor.NEO4J_BROWSER_URL))
                            .doNotEmbed());
                    break;
                }
            }
        }

        return cardPageBuildItem;
    }
}
