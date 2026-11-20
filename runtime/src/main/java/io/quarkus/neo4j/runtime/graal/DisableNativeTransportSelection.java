package io.quarkus.neo4j.runtime.graal;

import java.util.Map;

import org.neo4j.bolt.connection.netty.impl.async.connection.NettyTransport;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

final class DisableNativeTransportSelection {

    @TargetClass(className = "org.neo4j.bolt.connection.netty.impl.async.connection.EventLoopGroupFactory")
    static final class EventLoopGroupFactorySubstitutions {

        @Substitute
        public EventLoopGroup newEventLoopGroup(int threadCount) {
            return new MultiThreadIoEventLoopGroup(threadCount,
                    new DefaultThreadFactory("Neo4jDriverIO", true, Thread.MAX_PRIORITY), NioIoHandler.newFactory());
        }

    }

    @TargetClass(className = "org.neo4j.bolt.connection.netty.NettyBoltConnectionProviderFactory")
    static final class NettyBoltConnectionProviderFactorySubstitions {

        @Substitute
        private NettyTransport determineTransportType(
                System.Logger logger,
                LocalAddress localAddress,
                Map<String, ?> additionalConfig,
                String defaultNettyTransport) {
            return NettyTransport.nio();
        }
    }

}
