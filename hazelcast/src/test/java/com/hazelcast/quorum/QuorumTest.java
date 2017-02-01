/*
* Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*/

package com.hazelcast.quorum;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.QuorumConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.hazelcast.core.Member;
import com.hazelcast.quorum.impl.QuorumServiceImpl;
import com.hazelcast.spi.MemberAttributeServiceEvent;
import com.hazelcast.spi.MembershipAwareService;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

@RunWith(HazelcastSerialClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class QuorumTest extends HazelcastTestSupport {

    @Test
    public void testQuorumIsSetCorrectlyOnNodeInitialization() {
        Config config = new Config();
        QuorumConfig quorumConfig1 = new QuorumConfig();
        String quorumName1 = randomString();
        quorumConfig1.setName(quorumName1);
        quorumConfig1.setEnabled(true);
        quorumConfig1.setQuorumFunctionImplementation(new QuorumFunction() {
            @Override
            public boolean apply(Collection<Member> members) {
                return true;
            }
        });

        QuorumConfig quorumConfig2 = new QuorumConfig();
        String quorumName2 = randomString();
        quorumConfig2.setName(quorumName2);
        quorumConfig2.setEnabled(true);
        quorumConfig2.setSize(2);

        config.addQuorumConfig(quorumConfig1);
        config.addQuorumConfig(quorumConfig2);

        HazelcastInstance hazelcastInstance = createHazelcastInstance(config);
        final Quorum quorum1 = hazelcastInstance.getQuorumService().getQuorum(quorumName1);
        final Quorum quorum2 = hazelcastInstance.getQuorumService().getQuorum(quorumName2);
        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertTrue(quorum1.isPresent());
                assertFalse(quorum2.isPresent());
            }
        });
    }

    @Test
    public void testQuorumIgnoresMemberAttributeEvents() {
        Config config = new Config();
        QuorumConfig quorumConfig = new QuorumConfig().setName(randomString()).setEnabled(true);
        final RecordingQuorumFunction function = new RecordingQuorumFunction();
        quorumConfig.setQuorumFunctionImplementation(function);
        config.addQuorumConfig(quorumConfig);
        HazelcastInstance hazelcastInstance = createHazelcastInstance(config);
        NodeEngineImpl nodeEngine = getNodeEngineImpl(hazelcastInstance);
        MembershipAwareService service = nodeEngine.getService(QuorumServiceImpl.SERVICE_NAME);

        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertTrue(function.wasCalled);
            }
        });
        function.wasCalled = false;

        MemberAttributeServiceEvent event = mock(MemberAttributeServiceEvent.class);
        service.memberAttributeChanged(event);

        assertFalse(function.wasCalled);
    }

    @Test(expected = QuorumException.class)
    public void testCustomQuorumFunctionFails() {
        Config config = new Config();
        QuorumConfig quorumConfig = new QuorumConfig();
        String quorumName = randomString();
        quorumConfig.setName(quorumName);
        quorumConfig.setEnabled(true);
        quorumConfig.setQuorumFunctionImplementation(new QuorumFunction() {
            @Override
            public boolean apply(Collection<Member> members) {
                return false;
            }
        });
        config.addQuorumConfig(quorumConfig);
        String mapName = randomMapName();
        MapConfig mapConfig = new MapConfig(mapName);
        mapConfig.setQuorumName(quorumName);
        config.addMapConfig(mapConfig);
        HazelcastInstance hazelcastInstance = createHazelcastInstance(config);
        IMap<Object, Object> map = hazelcastInstance.getMap(mapName);
        map.put("1", "1");
    }

    @Test
    public void testCustomQuorumFunctionIsPresent() {
        Config config = new Config();
        QuorumConfig quorumConfig = new QuorumConfig();
        String quorumName = randomString();
        quorumConfig.setName(quorumName);
        quorumConfig.setEnabled(true);
        quorumConfig.setQuorumFunctionImplementation(new QuorumFunction() {
            @Override
            public boolean apply(Collection<Member> members) {
                return false;
            }
        });
        config.addQuorumConfig(quorumConfig);
        String mapName = randomMapName();
        MapConfig mapConfig = new MapConfig(mapName);
        mapConfig.setQuorumName(quorumName);
        config.addMapConfig(mapConfig);
        HazelcastInstance hazelcastInstance = createHazelcastInstance(config);
        IMap<Object, Object> map = hazelcastInstance.getMap(mapName);
        try {
            map.put("1", "1");
            fail();
        } catch (Exception ignored) {
        }
        Quorum quorum = hazelcastInstance.getQuorumService().getQuorum(quorumName);
        assertFalse(quorum.isPresent());
    }

    @Test(expected = QuorumException.class)
    public void testCustomQuorumFunctionFailsForAllNodes() {
        Config config = new Config();
        QuorumConfig quorumConfig = new QuorumConfig();
        String quorumName = randomString();
        quorumConfig.setName(quorumName);
        quorumConfig.setEnabled(true);
        quorumConfig.setQuorumFunctionImplementation(new QuorumFunction() {
            @Override
            public boolean apply(Collection<Member> members) {
                return false;
            }
        });
        config.addQuorumConfig(quorumConfig);
        String mapName = randomMapName();
        MapConfig mapConfig = new MapConfig(mapName);
        mapConfig.setQuorumName(quorumName);
        config.addMapConfig(mapConfig);
        TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(2);
        factory.newHazelcastInstance(config);
        HazelcastInstance hz = factory.newHazelcastInstance(config);
        IMap<Object, Object> map2 = hz.getMap(mapName);
        map2.put("1", "1");
    }

    @Test
    public void testCustomQuorumFunctionFailsThenSuccess() {
        Config config = new Config();
        QuorumConfig quorumConfig = new QuorumConfig();
        String quorumName = randomString();
        quorumConfig.setName(quorumName);
        quorumConfig.setEnabled(true);
        final AtomicInteger count = new AtomicInteger(1);
        quorumConfig.setQuorumFunctionImplementation(new QuorumFunction() {
            @Override
            public boolean apply(Collection<Member> members) {
                if (count.get() == 1) {
                    count.incrementAndGet();
                    return false;
                } else {
                    return true;
                }
            }
        });
        config.addQuorumConfig(quorumConfig);
        String mapName = randomMapName();
        MapConfig mapConfig = new MapConfig(mapName);
        mapConfig.setQuorumName(quorumName);
        config.addMapConfig(mapConfig);
        TestHazelcastInstanceFactory factory = new TestHazelcastInstanceFactory(2);
        HazelcastInstance hazelcastInstance = factory.newHazelcastInstance(config);
        IMap<Object, Object> map = hazelcastInstance.getMap(mapName);
        try {
            map.put("1", "1");
            fail();
        } catch (Exception e) {
            e.printStackTrace();
        }
        factory.newHazelcastInstance(config);
        map.put("1", "1");
        factory.shutdownAll();
    }

    @Test
    public void testOneQuorumsFailsOneQuorumSuccessForDifferentMaps() {
        TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(3);
        String fourNodeQuorum = randomString();
        QuorumConfig fourNodeQuorumConfig = new QuorumConfig(fourNodeQuorum, true);
        fourNodeQuorumConfig.setQuorumFunctionImplementation(new QuorumFunction() {
            @Override
            public boolean apply(Collection<Member> members) {
                return members.size() == 4;
            }
        });

        String threeNodeQuorum = randomString();
        QuorumConfig threeNodeQuorumConfig = new QuorumConfig(threeNodeQuorum, true);
        threeNodeQuorumConfig.setQuorumFunctionImplementation(new QuorumFunction() {
            @Override
            public boolean apply(Collection<Member> members) {
                return members.size() == 3;
            }
        });

        MapConfig fourNodeMapConfig = new MapConfig("fourNode");
        fourNodeMapConfig.setQuorumName(fourNodeQuorum);

        MapConfig threeNodeMapConfig = new MapConfig("threeNode");
        threeNodeMapConfig.setQuorumName(threeNodeQuorum);

        Config config = new Config();
        config.addQuorumConfig(threeNodeQuorumConfig);
        config.addQuorumConfig(fourNodeQuorumConfig);
        config.addMapConfig(fourNodeMapConfig);
        config.addMapConfig(threeNodeMapConfig);

        HazelcastInstance hz = factory.newHazelcastInstance(config);
        factory.newHazelcastInstance(config);
        factory.newHazelcastInstance(config);

        IMap<Object, Object> fourNode = hz.getMap("fourNode");
        IMap<Object, Object> threeNode = hz.getMap("threeNode");
        threeNode.put(generateKeyOwnedBy(hz), "bar");
        try {
            fourNode.put(generateKeyOwnedBy(hz), "bar");
            fail();
        } catch (Exception ignored) {
        }
    }

    @Test
    public void givenQuorumFunctionConfigured_whenImplementsHazelcastInstanceAware_thenHazelcastInjectsItsInstance() {
        String quorumName = randomString();
        QuorumConfig quorumConfig = new QuorumConfig(quorumName, true);
        quorumConfig.setQuorumFunctionClassName(HazelcastInstanceAwareQuorumFunction.class.getName());

        Config config = new Config();
        config.addQuorumConfig(quorumConfig);

        HazelcastInstance instance = createHazelcastInstance(config);

        assertEquals(instance, HazelcastInstanceAwareQuorumFunction.instance);
    }

    @Test
    public void givenQuorumFunctionInstanceConfigured_whenImplementsHazelcastInstanceAware_thenHazelcastInjectsItsInstance() {
        String quorumName = randomString();
        QuorumConfig quorumConfig = new QuorumConfig(quorumName, true);
        quorumConfig.setQuorumFunctionImplementation(new HazelcastInstanceAwareQuorumFunction());

        Config config = new Config();
        config.addQuorumConfig(quorumConfig);
        HazelcastInstance instance = createHazelcastInstance(config);

        assertEquals(instance, HazelcastInstanceAwareQuorumFunction.instance);
    }

    private static class HazelcastInstanceAwareQuorumFunction implements QuorumFunction, HazelcastInstanceAware {

        private static volatile HazelcastInstance instance;

        @Override
        public void setHazelcastInstance(HazelcastInstance instance) {
            HazelcastInstanceAwareQuorumFunction.instance = instance;
        }

        @Override
        public boolean apply(Collection<Member> members) {
            return false;
        }
    }

    private static class RecordingQuorumFunction implements QuorumFunction {

        private volatile boolean wasCalled;

        @Override
        public boolean apply(Collection<Member> members) {
            wasCalled = true;
            return false;
        }
    }
}
