package org.nd4j.parameterserver.distributed.transport;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.Header;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.parameterserver.distributed.conf.Configuration;
import org.nd4j.parameterserver.distributed.enums.NodeRole;
import org.nd4j.parameterserver.distributed.logic.Clipboard;
import org.nd4j.parameterserver.distributed.messages.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Transport implementation based on UDP unicast, for restricted environments, where multicast isn't available. I.e. AWS or Azure
 *
 * @author raver119@gmail.com
 */
@Slf4j
public class RoutedTransport extends BaseTransport {

    protected List<RemoteConnection> shards = new ArrayList<>();
    protected Map<Long, RemoteConnection> clients = new ConcurrentHashMap<>();

    @Override
    public void init(@NonNull Configuration configuration, @NonNull Clipboard clipboard, @NonNull NodeRole role, @NonNull String localIp, short shardIndex) {
        this.nodeRole = role;
        this.clipboard = clipboard;
        this.configuration = configuration;
        this.shardIndex = shardIndex;
        this.messages = new LinkedBlockingQueue<>(128);

        context = new Aeron.Context();
        driver = MediaDriver.launchEmbedded();
        context.aeronDirectoryName(driver.aeronDirectoryName());
        aeron = Aeron.connect(context);


        /*
            Regardless of current role, we raise subscription for incoming messages channel
         */
        ip = localIp;
        unicastChannelUri = "aeron:udp?endpoint=" + ip + ":" + port;
        subscriptionForClients = aeron.addSubscription(unicastChannelUri, configuration.getStreamId());

        messageHandlerForClients = new FragmentAssembler(
                (buffer, offset, length, header) -> jointMessageHandler(buffer, offset, length, header)
        );

        /*
            Now, regardless of curent role, we set up publication channel to each shard
         */
        String shardChannelUri = null;
        String remoteIp = null;
        int remotePort = 0;
        for (String ip: configuration.getShardAddresses()){
            if (ip.contains(":")) {
                shardChannelUri = "aeron:udp?endpoint=" + ip;
                String[] split = ip.split(":");
                remoteIp = split[0];
                remotePort = Integer.valueOf(split[1]);
            } else {
                shardChannelUri = "aeron:udp?endpoint=" + ip + ":" + configuration.getUnicastPort();
                remoteIp = ip;
                remotePort = configuration.getUnicastPort();
            }

            Publication publication = aeron.addPublication(shardChannelUri, configuration.getStreamId());

            RemoteConnection connection = RemoteConnection.builder()
                    .ip(remoteIp)
                    .port(remotePort)
                    .publication(publication)
                    .build();

            shards.add(connection);
        }



        switch (nodeRole) {
            case MASTER:
            case BACKUP: {

            }
            case SHARD: {
                    /*
                        For unicast transport we want to have interconnects between all shards first of all, because we know their IPs in advance.
                        But due to design requirements, clients have the same first step, so it's kinda shared for all states :)
                     */

                    /*
                        Next step is connections setup for backup nodes.
                        TODO: to be implemented
                     */

                }
                break;
            case CLIENT: {
                    /*
                        For Clients on unicast transport, we either set up connection to single Shard, or to multiple shards
                        But since this code is shared - we don't do anything here
                     */
                }
                break;
            default:
                throw new ND4JIllegalStateException("Unknown NodeRole being passed: " + nodeRole);
        }
    }

    /**
     * This method implements Shard -> Shards comms
     *
     * @param message
     */
    @Override
    protected void sendCoordinationCommand(VoidMessage message) {
/*
        Queue<RemoteConnection> queue = new ArrayDeque<>(shards);

        long result = 0;

        // TODO: probably per-connection retries counter? however, probably doesn't matters why exactly RuntimeException will be thrown
        long retries = 0;

        while (!queue.isEmpty()) {
            RemoteConnection rc = queue.poll();
            result = rc.getPublication().offer(message.asUnsafeBuffer());

            if (result < 0)
                queue.add(rc);

            if (queue.size() == 1)
                try {
                    Thread.sleep(configuration.getRetransmitTimeout());
                } catch (Exception e) {}

            // not_connected
            if (result == -1)
                retries++;

            if (retries > 20)
                throw new RuntimeException("NOT_CONNECTED: " + rc.getIp() + ":" + rc.getPort());
        }
*/

        // TODO: check which approach is faster, lambda, direct roll through list, or queue approach
        shards.parallelStream().forEach((rc) ->{
            long res = 0;
            long retr = 0;

            while ((res = rc.getPublication().offer(message.asUnsafeBuffer())) < 0L) {
                if (res == -1)
                    retr++;

                if (retr > 20)
                    throw new RuntimeException("NOT_CONNECTED: " + rc.getIp() + ":" + rc.getPort());
                else
                    try {
                        Thread.sleep(configuration.getRetransmitTimeout());
                    } catch (Exception e) {}

            }
        });
    }

    /**
     * This method implements Shard -> Client comms
     *
     * @param message
     */
    @Override
    protected void sendFeedbackToClient(VoidMessage message) {
        // FIXME: add address field here
        long targetAddress = message.getTaskId();
        long result = 0;
        while ((result = clients.get(targetAddress).getPublication().offer(message.asUnsafeBuffer())) < 0L) {
            try {
                Thread.sleep(50);
            } catch (Exception e) { }
        }
    }

    @Override
    protected void shutdownSilent() {
        // closing shards
        shards.forEach((rc) -> {
            rc.getPublication().close();
        });

        // closing clients connections
        clients.values().forEach((rc) -> {
            rc.getPublication().close();
        });

        subscriptionForClients.close();
    }

    @Override
    public void shutdown() {
        runner.set(false);

        if (threadB != null)
            threadB.interrupt();

        if (threadA != null)
            threadA.interrupt();

        shutdownSilent();
    }

    /**
     * This message handler is responsible for receiving messages on any side of p2p network
     *
     * @param buffer
     * @param offset
     * @param length
     * @param header
     */
    protected void jointMessageHandler(DirectBuffer buffer, int offset, int length, Header header) {
        /**
         *  All incoming messages here are supposed to be "just messages", only unicast communication
         *  All of them should implement MeaningfulMessage interface
         */

        byte[] data = new byte[length];
        buffer.getBytes(offset, data);

        VoidMessage message = VoidMessage.fromBytes(data);
        if (message instanceof MeaningfulMessage) {
            MeaningfulMessage msg = (MeaningfulMessage) message;
            completed.put(message.getTaskId(), msg);
        } else if (message instanceof RequestMessage) {
            try {
                messages.put((RequestMessage) message);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (message instanceof DistributedMessage) {
            try {
                messages.put((DistributedMessage) message);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (message instanceof TrainingMessage) {
            try {
                messages.put((TrainingMessage) message);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Data
    @Builder
    public static class RemoteConnection {
        private String ip;
        private int port;
        private Publication publication;
    }

}
