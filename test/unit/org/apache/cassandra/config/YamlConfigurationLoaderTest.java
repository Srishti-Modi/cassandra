/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.distributed.shared.WithProperties;
import org.apache.cassandra.io.util.File;

import static org.apache.cassandra.config.CassandraRelevantProperties.CONFIG_ALLOW_SYSTEM_PROPERTIES;
import static org.apache.cassandra.config.YamlConfigurationLoader.SYSTEM_PROPERTY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;


public class YamlConfigurationLoaderTest
{
    @Test
    public void updateInPlace()
    {
        Config config = new Config();
        Map<String, Object> map = ImmutableMap.<String, Object>builder().put("storage_port", 123)
                                                                        .put("commitlog_sync", Config.CommitLogSync.batch)
                                                                        .put("seed_provider.class_name", "org.apache.cassandra.locator.SimpleSeedProvider")
                                                                        .put("client_encryption_options.cipher_suites", Collections.singletonList("FakeCipher"))
                                                                        .put("client_encryption_options.optional", false)
                                                                        .put("client_encryption_options.enabled", true)
                                                                        .build();
        Config updated = YamlConfigurationLoader.updateFromMap(map, true, config);
        assert updated == config : "Config pointers do not match";
        assertThat(config.storage_port).isEqualTo(123);
        assertThat(config.commitlog_sync).isEqualTo(Config.CommitLogSync.batch);
        assertThat(config.seed_provider.class_name).isEqualTo("org.apache.cassandra.locator.SimpleSeedProvider");
        assertThat(config.client_encryption_options.cipher_suites).isEqualTo(Collections.singletonList("FakeCipher"));
        assertThat(config.client_encryption_options.optional).isFalse();
        assertThat(config.client_encryption_options.enabled).isTrue();
    }

    @Test
    public void withSystemProperties()
    {
        // for primitive types or data-types which use a String constructor, we can support these as nested
        // if the type is a collection, then the string format doesn't make sense and will fail with an error such as
        //   Cannot create property=client_encryption_options.cipher_suites for JavaBean=org.apache.cassandra.config.Config@1f59a598
        //   No single argument constructor found for interface java.util.List : null
        // the reason is that its not a scalar but a complex type (collection type), so the map we use needs to have a collection to match.
        // It is possible that we define a common string representation for these types so they can be written to; this
        // is an issue that SettingsTable may need to worry about.
        try (WithProperties ignore = new WithProperties(CONFIG_ALLOW_SYSTEM_PROPERTIES.getKey(), "true",
                                                        SYSTEM_PROPERTY_PREFIX + "storage_port", "123",
                                                        SYSTEM_PROPERTY_PREFIX + "commitlog_sync", "batch",
                                                        SYSTEM_PROPERTY_PREFIX + "seed_provider.class_name", "org.apache.cassandra.locator.SimpleSeedProvider",
//                                                        PROPERTY_PREFIX + "client_encryption_options.cipher_suites", "[\"FakeCipher\"]",
                                                        SYSTEM_PROPERTY_PREFIX + "client_encryption_options.optional", "false",
                                                        SYSTEM_PROPERTY_PREFIX + "client_encryption_options.enabled", "true",
                                                        SYSTEM_PROPERTY_PREFIX + "doesnotexist", "true"
        ))
        {
            Config config = YamlConfigurationLoader.fromMap(Collections.emptyMap(), true, Config.class);
            assertThat(config.storage_port).isEqualTo(123);
            assertThat(config.commitlog_sync).isEqualTo(Config.CommitLogSync.batch);
            assertThat(config.seed_provider.class_name).isEqualTo("org.apache.cassandra.locator.SimpleSeedProvider");
//            assertThat(config.client_encryption_options.cipher_suites).isEqualTo(Collections.singletonList("FakeCipher"));
            assertThat(config.client_encryption_options.optional).isFalse();
            assertThat(config.client_encryption_options.enabled).isTrue();
        }
    }

    @Test
    public void readThresholdsFromConfig()
    {
        Config c = load("test/conf/cassandra.yaml");

        assertThat(c.read_thresholds_enabled).isTrue();

        assertThat(c.coordinator_read_size_warn_threshold).isEqualTo(DataStorageSpec.inKibibytes(1 << 10));
        assertThat(c.coordinator_read_size_fail_threshold).isEqualTo(DataStorageSpec.inKibibytes(1 << 12));

        assertThat(c.local_read_size_warn_threshold).isEqualTo(DataStorageSpec.inKibibytes(1 << 12));
        assertThat(c.local_read_size_fail_threshold).isEqualTo(DataStorageSpec.inKibibytes(1 << 13));

        assertThat(c.row_index_read_size_warn_threshold).isEqualTo(DataStorageSpec.inKibibytes(1 << 12));
        assertThat(c.row_index_read_size_fail_threshold).isEqualTo(DataStorageSpec.inKibibytes(1 << 13));
    }

    @Test
    public void readThresholdsFromMap()
    {

        Map<String, Object> map = ImmutableMap.of(
        "read_thresholds_enabled", true,
        "coordinator_read_size_warn_threshold", "1024KiB",
        "local_read_size_fail_threshold", "1024KiB",
        "row_index_read_size_warn_threshold", "1024KiB",
        "row_index_read_size_fail_threshold", "1024KiB"
        );

        Config c = YamlConfigurationLoader.fromMap(map, Config.class);
        assertThat(c.read_thresholds_enabled).isTrue();

        assertThat(c.coordinator_read_size_warn_threshold).isEqualTo(DataStorageSpec.inKibibytes(1024));
        assertThat(c.coordinator_read_size_fail_threshold).isNull();

        assertThat(c.local_read_size_warn_threshold).isNull();
        assertThat(c.local_read_size_fail_threshold).isEqualTo(DataStorageSpec.inKibibytes(1024));

        assertThat(c.row_index_read_size_warn_threshold).isEqualTo(DataStorageSpec.inKibibytes(1024));
        assertThat(c.row_index_read_size_fail_threshold).isEqualTo(DataStorageSpec.inKibibytes(1024));
    }

    @Test
    public void fromMapTest()
    {
        int storagePort = 123;
        Config.CommitLogSync commitLogSync = Config.CommitLogSync.batch;
        ParameterizedClass seedProvider = new ParameterizedClass("org.apache.cassandra.locator.SimpleSeedProvider", Collections.emptyMap());
        Map<String,Object> encryptionOptions = ImmutableMap.of("cipher_suites", Collections.singletonList("FakeCipher"),
                                                               "optional", false,
                                                               "enabled", true);
        Map<String,Object> map = new ImmutableMap.Builder<String, Object>()
                                 .put("storage_port", storagePort)
                                 .put("commitlog_sync", commitLogSync)
                                 .put("seed_provider", seedProvider)
                                 .put("client_encryption_options", encryptionOptions)
                                 .put("internode_socket_send_buffer_size", "5B")
                                 .put("internode_socket_receive_buffer_size", "5B")
                                 .put("commitlog_sync_group_window_in_ms", "42")
                                 .build();

        Config config = YamlConfigurationLoader.fromMap(map, Config.class);
        assertEquals(storagePort, config.storage_port); // Check a simple integer
        assertEquals(commitLogSync, config.commitlog_sync); // Check an enum
        assertEquals(seedProvider, config.seed_provider); // Check a parameterized class
        assertEquals(false, config.client_encryption_options.optional); // Check a nested object
        assertEquals(true, config.client_encryption_options.enabled); // Check a nested object
        assertEquals(new DataStorageSpec("5B"), config.internode_socket_send_buffer_size); // Check names backward compatibility (CASSANDRA-17141 and CASSANDRA-15234)
        assertEquals(new DataStorageSpec("5B"), config.internode_socket_receive_buffer_size); // Check names backward compatibility (CASSANDRA-17141 and CASSANDRA-15234)
    }

    @Test
    public void typeChange()
    {
        Config old = YamlConfigurationLoader.fromMap(ImmutableMap.of("key_cache_save_period", 42,
                                                                     "row_cache_save_period", 42,
                                                                     "counter_cache_save_period", 42), Config.class);
        Config latest = YamlConfigurationLoader.fromMap(ImmutableMap.of("key_cache_save_period", "42s",
                                                                        "row_cache_save_period", "42s",
                                                                        "counter_cache_save_period", "42s"), Config.class);
        assertThat(old.key_cache_save_period).isEqualTo(latest.key_cache_save_period).isEqualTo(SmallestDurationSeconds.inSeconds(42));
        assertThat(old.row_cache_save_period).isEqualTo(latest.row_cache_save_period).isEqualTo(SmallestDurationSeconds.inSeconds(42));
        assertThat(old.counter_cache_save_period).isEqualTo(latest.counter_cache_save_period).isEqualTo(SmallestDurationSeconds.inSeconds(42));
    }

    @Test
    public void sharedErrorReportingExclusions()
    {
        Config config = load("data/config/YamlConfigurationLoaderTest/shared_client_error_reporting_exclusions.yaml");
        SubnetGroups expected = new SubnetGroups(Arrays.asList("127.0.0.1", "127.0.0.0/31"));
        assertThat(config.client_error_reporting_exclusions).isEqualTo(expected);
        assertThat(config.internode_error_reporting_exclusions).isEqualTo(expected);
    }

    @Test
    public void converters()
    {
        // MILLIS_DURATION
        assertThat(from("permissions_validity_in_ms", "42").permissions_validity.toMilliseconds()).isEqualTo(42);
        assertThatThrownBy(() -> from("permissions_validity", -2).permissions_validity.toMilliseconds())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid duration: -2 Accepted units:[MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS] where case matters and only non-negative values.");

        // MILLIS_DOUBLE_DURATION
        assertThat(from("commitlog_sync_group_window_in_ms", "42").commitlog_sync_group_window.toMilliseconds()).isEqualTo(42);
        assertThat(from("commitlog_sync_group_window_in_ms", "0.2").commitlog_sync_group_window.toMilliseconds()).isEqualTo(0);
        assertThat(from("commitlog_sync_group_window_in_ms", "42.5").commitlog_sync_group_window.toMilliseconds()).isEqualTo(43);
        assertThat(from("commitlog_sync_group_window_in_ms", "NaN").commitlog_sync_group_window.toMilliseconds()).isEqualTo(0);
        assertThatThrownBy(() -> from("commitlog_sync_group_window_in_ms", -2).commitlog_sync_group_window.toMilliseconds())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid duration -2: value must be positive");

        // MILLIS_CUSTOM_DURATION
        assertThat(from("permissions_update_interval_in_ms", 42).permissions_update_interval).isEqualTo(SmallestDurationMilliseconds.inMilliseconds(42));
        assertThat(from("permissions_update_interval_in_ms", -1).permissions_update_interval).isNull();
        assertThatThrownBy(() -> from("permissions_update_interval_in_ms", -2))
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid duration -2: value must be positive");

        // SECONDS_DURATION
        assertThat(from("streaming_keep_alive_period_in_secs", "42").streaming_keep_alive_period.toSeconds()).isEqualTo(42);
        assertThatThrownBy(() -> from("streaming_keep_alive_period_in_secs", -2).streaming_keep_alive_period.toSeconds())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid duration -2: value must be positive");

        // NEGATIVE_SECONDS_DURATION
        assertThat(from("validation_preview_purge_head_start_in_sec", -1).validation_preview_purge_head_start.toSeconds()).isEqualTo(0);
        assertThat(from("validation_preview_purge_head_start_in_sec", 0).validation_preview_purge_head_start.toSeconds()).isEqualTo(0);
        assertThat(from("validation_preview_purge_head_start_in_sec", 42).validation_preview_purge_head_start.toSeconds()).isEqualTo(42);

        // SECONDS_CUSTOM_DURATION already tested in type change

        // MINUTES_DURATION
        assertThat(from("index_summary_resize_interval_in_minutes", "42").index_summary_resize_interval.toMinutes()).isEqualTo(42);
        assertThatThrownBy(() -> from("index_summary_resize_interval_in_minutes", -2).index_summary_resize_interval.toMinutes())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid duration -2: value must be positive");

        // BYTES_CUSTOM_DATASTORAGE
        assertThat(from("native_transport_max_concurrent_requests_in_bytes_per_ip", -1).native_transport_max_request_data_in_flight_per_ip).isEqualTo(null);
        assertThat(from("native_transport_max_concurrent_requests_in_bytes_per_ip", 0).native_transport_max_request_data_in_flight_per_ip.toBytes()).isEqualTo(0);
        assertThat(from("native_transport_max_concurrent_requests_in_bytes_per_ip", 42).native_transport_max_request_data_in_flight_per_ip.toBytes()).isEqualTo(42);

        // MEBIBYTES_DATA_STORAGE
        assertThat(from("memtable_heap_space_in_mb", "42").memtable_heap_space.toMebibytes()).isEqualTo(42);
        assertThatThrownBy(() -> from("memtable_heap_space_in_mb", -2).memtable_heap_space.toMebibytes())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid data storage: value must be positive, but was -2");

        // KIBIBYTES_DATASTORAGE
        assertThat(from("column_index_size_in_kb", "42").column_index_size.toKibibytes()).isEqualTo(42);
        assertThatThrownBy(() -> from("column_index_size_in_kb", -2).column_index_size.toMebibytes())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid data storage: value must be positive, but was -2");

        // BYTES_DATASTORAGE
        assertThat(from("internode_max_message_size_in_bytes", "42").internode_max_message_size.toBytes()).isEqualTo(42);
        assertThatThrownBy(() -> from("internode_max_message_size_in_bytes", -2).internode_max_message_size.toBytes())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid data storage: value must be positive, but was -2");

        // BYTES_DATASTORAGE
        assertThat(from("internode_max_message_size_in_bytes", "42").internode_max_message_size.toBytes()).isEqualTo(42);
        assertThatThrownBy(() -> from("internode_max_message_size_in_bytes", -2).internode_max_message_size.toBytes())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid data storage: value must be positive, but was -2");

        // MEBIBYTES_PER_SECOND_DATA_RATE
        assertThat(from("compaction_throughput_mb_per_sec", "42").compaction_throughput.toMebibytesPerSecondAsInt()).isEqualTo(42);
        assertThatThrownBy(() -> from("compaction_throughput_mb_per_sec", -2).compaction_throughput.toMebibytesPerSecondAsInt())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid bit rate: value must be non-negative");

        // MEGABITS_TO_MEBIBYTES_PER_SECOND_DATA_RATE
        assertThat(from("stream_throughput_outbound_megabits_per_sec", "42").stream_throughput_outbound.toMegabitsPerSecondAsInt()).isEqualTo(42);
        assertThatThrownBy(() -> from("stream_throughput_outbound_megabits_per_sec", -2).stream_throughput_outbound.toMegabitsPerSecondAsInt())
        .hasRootCauseInstanceOf(ConfigurationException.class)
        .hasRootCauseMessage("Invalid bit rate: value must be non-negative");
    }

    private static Config from(Object... values)
    {
        assert values.length % 2 == 0 : "Map can only be created with an even number of inputs: given " + values.length;
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (int i = 0; i < values.length; i += 2)
            builder.put((String) values[i], values[i + 1]);
        return YamlConfigurationLoader.fromMap(builder.build(), Config.class);
    }

    private static Config load(String path)
    {
        URL url = YamlConfigurationLoaderTest.class.getClassLoader().getResource(path);
        if (url == null)
        {
            try
            {
                url = new File(path).toPath().toUri().toURL();
            }
            catch (MalformedURLException e)
            {
                throw new AssertionError(e);
            }
        }
        return new YamlConfigurationLoader().loadConfig(url);
    }
}
