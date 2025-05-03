package org.hyperledger.fabric.samples.assettransfer;

import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType()
public class Model {
    //用户标识
    @Property()
    private final String t;

    @Property()
    private final String param;

    @Property()
    private final String feature_size;

    @Property()
    private final String channelId;

    public String getT() {
        return t;
    }

    public String getParam() {
        return param;
    }

    public String getFeature_size() {
        return feature_size;
    }

    public String getChannelId() {
        return channelId;
    }

    public Model(@JsonProperty("param") final String param,
                 @JsonProperty("feature_size") final String feature_size,
                 @JsonProperty("t") final String t,
                 @JsonProperty("") final String c
                 ) {
        this.param = param;
        this.feature_size = feature_size;
        this.t = t;
        this.channelId = c;
    }
}
