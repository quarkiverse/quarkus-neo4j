package io.quarkus.neo4j.runtime.graal;

import java.util.Map;
import java.util.concurrent.ThreadFactory;

import org.neo4j.bolt.connection.netty.impl.async.connection.NettyTransport;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;

final class DisableNativeTransportSelection {

    @TargetClass(className = "org.neo4j.bolt.connection.netty.impl.async.connection.EventLoopGroupFactory")
    static final class EventLoopGroupFactorySubstitutions {

        @Substitute
        public EventLoopGroup newEventLoopGroup(int threadCount) {
            return new DriverEventLoopGroup(threadCount);
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

    private static final class DriverEventLoopGroup extends NioEventLoopGroup {
        DriverEventLoopGroup(int nThreads) {
            super(nThreads);
        }

        @Override
        protected ThreadFactory newDefaultThreadFactory() {
            return new DefaultThreadFactory("Neo4jDriverIO", true, Thread.MAX_PRIORITY);
        }
    }

}
