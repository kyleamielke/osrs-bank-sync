package io.kyil.osrsbanksync;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class BankSnapshotSerializationTest
{
    private final Gson wireGson = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .serializeNulls()
        .create();

    @Test
    public void testWireJsonMatchesFixture()
    {
        BankSnapshot snapshot = new BankSnapshot(
            1234567890123456789L,
            "Zezima",
            "IRONMAN",
            "2025-01-15T22:31:04Z",
            "0.1.0",
            "550e8400-e29b-41d4-a716-446655440000",
            Arrays.asList(
                new BankItem(0, 995, Integer.MAX_VALUE),
                new BankItem(1, 4151, 1)
            )
        );

        String actual = wireGson.toJson(snapshot);
        String fixture = "{\n"
            + "  \"account_hash\": 1234567890123456789,\n"
            + "  \"display_name\": \"Zezima\",\n"
            + "  \"account_type\": \"IRONMAN\",\n"
            + "  \"captured_at\": \"2025-01-15T22:31:04Z\",\n"
            + "  \"plugin_version\": \"0.1.0\",\n"
            + "  \"snapshot_id\": \"550e8400-e29b-41d4-a716-446655440000\",\n"
            + "  \"items\": [\n"
            + "    {\"slot\": 0, \"item_id\": 995, \"quantity\": 2147483647},\n"
            + "    {\"slot\": 1, \"item_id\": 4151, \"quantity\": 1}\n"
            + "  ]\n"
            + "}";

        JsonParser parser = new JsonParser();
        Assert.assertEquals(parser.parse(fixture), parser.parse(actual));
    }

    @Test
    public void testNullableFieldsSerializedAsNull()
    {
        BankSnapshot snapshot = new BankSnapshot(
            1L,
            null,
            null,
            "2025-01-15T22:31:04Z",
            "0.1.0",
            "550e8400-e29b-41d4-a716-446655440000",
            Collections.emptyList()
        );

        JsonParser parser = new JsonParser();
        JsonObject object = parser.parse(wireGson.toJson(snapshot)).getAsJsonObject();
        Assert.assertTrue(object.has("display_name"));
        Assert.assertTrue(object.has("account_type"));
        Assert.assertTrue(object.get("display_name").isJsonNull());
        Assert.assertTrue(object.get("account_type").isJsonNull());
    }
}
