package com.netflix.ndbench.plugin.local.dynomite.proxy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;
import com.netflix.dyno.connectionpool.ConnectionPoolConfiguration.LoadBalancingStrategy;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.OperationResult;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.ndbench.api.plugin.DataGenerator;
import com.netflix.ndbench.api.plugin.NdBenchClient;
import com.netflix.ndbench.api.plugin.annotations.NdBenchClientPlugin;
import com.netflix.ndbench.plugin.dyno.DynoJedis;

/**
 * @author diegopacheco
 */
@Singleton
@NdBenchClientPlugin("LocalDynomiteProxyPlugin")
public class LocalDynomiteProxyPlugin implements NdBenchClient{
    private static final Logger logger = LoggerFactory.getLogger(DynoJedis.class);

    private static final String ResultOK = "Ok";
    private static final String CacheMiss = null;

    private static final String ClusterName = "dynomite_redis_puneet";

    private DataGenerator dataGenerator;

    private final AtomicReference<DynoJedisClient> jedisClient = new AtomicReference<DynoJedisClient>(null);


    @Override
    public void init(DataGenerator dataGenerator) throws Exception {
        this.dataGenerator = dataGenerator;
        if (jedisClient.get() != null) {
            return;
        }

        logger.info("Initing dyno jedis client");

        logger.info("\nDynomite Cluster: " + ClusterName);


        HostSupplier hSupplier = new HostSupplier() {

            @Override
            public Collection<Host> getHosts() {

                List<Host> hosts = new ArrayList<Host>();
                hosts.add(new Host("localhost", 8102, Host.Status.Up).setRack("local-dc"));

                return hosts;
            }

        };



        DynoJedisClient jClient = new DynoJedisClient.Builder()
                .withApplicationName(ClusterName)
                .withDynomiteClusterName(ClusterName)
                .withHostSupplier(hSupplier)
                .withCPConfig(new ConnectionPoolConfigurationImpl("myCP")
                .withTokenSupplier(new LocalHttpEndpointBasedTokenMapSupplier(8102))
                .setLoadBalancingStrategy(LoadBalancingStrategy.TokenAware))                		
                .build();

        jedisClient.set(jClient);

    }

    @Override
    public String readSingle(String key) throws Exception {

        String res = jedisClient.get().get(key);

        if(res!=null)
        {
            if(res.isEmpty())
            {
                throw new Exception("Data retrieved is not ok ");
            }
        }
        else
        {
            return CacheMiss;
        }

        return ResultOK;

    }

    @Override
    public String writeSingle(String key) throws Exception {
        OperationResult<String> result = jedisClient.get().d_set(key, dataGenerator.getRandomValue());

        if (!"OK".equals(result.getResult())) {
            logger.error("SET_ERROR: GOT " + result.getResult() + " for SET operation");
            throw new RuntimeException(String.format("DynoJedis: value %s for SET operation is NOT VALID", key));

        }

        return result.getResult();
    }

    /**
     *
     */
    @Override
    public void shutdown() throws Exception {
        if (jedisClient.get() != null) {
            jedisClient.get().stopClient();
            jedisClient.set(null);
        }
    }

    /**
     * shutdown the client
     */
    @Override
    public String getConnectionInfo() throws Exception {
        return String.format("DynoJedisPlugin - ConnectionInfo ::Cluster Name - %s", ClusterName);
    }

    @Override
    public String runWorkFlow() throws Exception {
        return null;
    }
}