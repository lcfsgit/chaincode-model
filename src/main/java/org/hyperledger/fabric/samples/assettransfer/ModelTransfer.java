/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;


import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import com.owlike.genson.Genson;
import org.json.JSONObject;
import org.json.JSONPointer;


@Contract(
        name = "model",
        info = @Info(
                title = "Model Transfer",
                description = "The hyperlegendary model transfer",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "a.transfer@example.com",
                        name = "Adrian Transfer",
                        url = "https://hyperledger.example.com")))
@Default
public final class ModelTransfer implements ContractInterface {

    private final Genson genson = new Genson();

    private enum AssetTransferErrors {
        ASSET_NOT_FOUND,
        ASSET_ALREADY_EXISTS
    }

    private static Map<String,Map<String, List<List<Double>>>> channelModels = new HashMap<>();

    /*上传本地模型和数据集特征大小*/
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Model CreateAsset(final Context ctx, final String param,
                             final String featureSize, final String t) {
        ChaincodeStub stub = ctx.getStub();
        String txid = stub.getTxId();
        String channelId = stub.getChannelId();

        if (AssetExists(ctx, t)) {
            String errorMessage = String.format("Asset %s already exists", txid);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_ALREADY_EXISTS.toString());
        }

        Model model = new Model(param, featureSize, t,channelId);
        // Use Genson to convert the Asset into string, sort it alphabetically and serialize it into a json string
        String sortedJson = genson.serialize(model);
        stub.putStringState(t, sortedJson);

        return model;
    }

    /*通道模型质量评估算法*/
    public List<Double> AggregateChannelModel(final Context ctx) {
        HashMap<String, List<Integer>> featuresMap = new HashMap<>();
        ChaincodeStub stub = ctx.getStub();
        String channelId = stub.getChannelId();

        //获取通道内所有本地模型
        List<Model> models = GetAllAssetsList(ctx);
        //筛选属于本通道的模型
        List<Model> channelModels = new ArrayList<>();
        for (Model model : models) {
            if (model.getChannelId().equals(channelId)) {
                channelModels.add(model);
            }
        }
        //质量评估
        for (Model model : models) {
            //解析features
            List<Integer> feature = parseStringListToIntList(model.getFeature_size(), ",");
            featuresMap.put(model.getT(), feature);
        }
        // 初始化列表d,|DS[w]|
        List<Integer> d = new ArrayList<>();
        for (List<Integer> feature : featuresMap.values()) {
            int total = sum(feature); // 使用sum方法计算列表中所有元素的和
            d.add(total);
        }
        // 将d复制到列表d_prime
        List<Integer> d_prime = new ArrayList<>(d);
        // 将列表d按照升序排序
        Collections.sort(d);
        // 计算low和high
        int low = d.get((int) (0.25 * d.size()));
        int high = d.get((int) (0.75 * d.size()));
        // 初始化权重列表weights
        List<Double> weights = new ArrayList<>();
        for (int i = 0; i < d.size(); i++) {
            double weight = 0;
            if (d.get(i) == 0) {
                weight = 0;
            } else if (d.get(i) < low) {
                weight = (double) d.get(i) / low;
            } else if (d.get(i) > high) {
                weight = (1 - (d.get(i) - high)) / (2 * high - low);
            } else {
                weight = 1;
            }
            weights.add(Math.max(0, Math.min(weight, 1)));
        }
        return weights;
    }

    /*全局模型质量评估算法*/
    public JSONObject AggregateGlobalModel(final Context ctx) {
        //获取所有资产
        List<Model> models = GetAllAssetsList(ctx);
        //质量评估
        //按照channelid属性给models分组
        Map<String, List<Model>> channelModels = models.stream()
                .collect(Collectors.groupingBy(Model::getChannelId));
        //获取每个key下对应的模型集合中，feature属性的总和
        Map<String, Integer> counts = channelModels.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    List<Model> modelsInChannel = entry.getValue();
                    return modelsInChannel.stream()
                            .mapToInt(model -> parseStringListToIntList(model.getFeature_size(), ",").stream()
                                    .mapToInt(Integer::intValue)
                                    .sum())
                            .sum();
                }));
        //获取counts所有value的和
        int sum = counts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        //获取counts每个key下，value占总和的比例
        Map<String, Double> CG = channelModels.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    List<Model> modelsInChannel = entry.getValue();
                    return (double) modelsInChannel.size() / sum;
                }));
        JSONObject json = new JSONObject();
        json.put("CG", CG);
        json.put("channelModelsGroup", channelModels);
        return json;
    }
    /**
     * 初始化本地模型，接收社交关系权重列表
     *
     * @param ctx ctx
     * @return {@link Task}
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String InitLocalModel(final Context ctx, final String weights,String t) throws JsonProcessingException {
        //获取所有本地模型
        List<Model> models = GetAllAssetsList(ctx);
        //筛选models中t为t的模型
        List<Model> localModels = models.stream()
                .filter(model -> !model.getT().equals(t))
                .collect(Collectors.toList());
        //将json字符串weights使用fastjson解析为json对象
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(weights);
        //初始化本地模型
        // 按照models中的顺序，依次将jsonNode中key=t的value依次追加到新List中
        List<Double> social_weights = new ArrayList<>();
        for (int i = 0; i < localModels.size(); i++) {
            social_weights.add(jsonNode.get(localModels.get(i).getT()).asDouble());
        }
        //模型聚合
        Map<String, List<List<Double>>> local=aggregate(social_weights,localModels);
        return local.toString();
    }


    /**
     * 下载全局模型
     *
     * @param ctx ctx
     * @return {@link Task}
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String ReadAsset(final Context ctx, final String t) {
        ChaincodeStub stub = ctx.getStub();
        String modelJSON = stub.getStringState(t);

        if (modelJSON == null || modelJSON.isEmpty()) {
            String errorMessage = String.format("Asset %s does not exist", t);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        Model model = genson.deserialize(modelJSON, Model.class);
        // 通道聚合
        List<Model> models = GetAllAssetsList(ctx);
        List<Double> weights = AggregateChannelModel(ctx);
        Map<String, List<List<Double>>>channelModel = aggregate(weights,models);
        channelModels.put(t,channelModel);
        // 全局聚合
        Map<String, Double>CG= (Map<String, Double>) AggregateGlobalModel(ctx).get("CG");
        Map<String, List<Model>> channelModelsGroup = (Map<String, List<Model>>) AggregateGlobalModel(ctx).get("channelModelsGroup");
        //获取CG的value列表和channelModels的value列表
        List<Double> global_weights = new ArrayList<>(CG.values());
        List<Model> channel_models = new ArrayList<>();
        for (Map.Entry<String, List<Model>> entry : channelModelsGroup.entrySet()) {
            channel_models.addAll(entry.getValue());
        }
        //聚合全局模型
        Map<String, List<List<Double>>> global=aggregate(global_weights,channel_models);
        // 返回聚合后的模型
        return global.toString();
    }


    /**
     * Retrieves all assets from the ledger.
     *
     * @param ctx the transaction context
     * @return array of assets found on the ledger
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAllAssets(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        List<Model> queryResults = new ArrayList<Model>();

        // To retrieve all assets from the ledger use getStateByRange with empty startKey & endKey.
        // Giving empty startKey & endKey is interpreted as all the keys from beginning to end.
        // As another example, if you use startKey = 'asset0', endKey = 'asset9' ,
        // then getStateByRange will retrieve task with keys between asset0 (inclusive) and asset9 (exclusive) in lexical order.
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("", "");

        for (KeyValue result : results) {
            Model model = genson.deserialize(result.getStringValue(), Model.class);
            System.out.println(model);
            queryResults.add(model);
        }

        final String response = genson.serialize(queryResults);

        return response;
    }

    /**
     * Retrieves all assets from the ledger.
     *
     * @param ctx the transaction context
     * @return array of assets found on the ledger
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public List<Model> GetAllAssetsList(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        List<Model> queryResults = new ArrayList<Model>();

        // To retrieve all assets from the ledger use getStateByRange with empty startKey & endKey.
        // Giving empty startKey & endKey is interpreted as all the keys from beginning to end.
        // As another example, if you use startKey = 'asset0', endKey = 'asset9' ,
        // then getStateByRange will retrieve task with keys between asset0 (inclusive) and asset9 (exclusive) in lexical order.
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("", "");

        for (KeyValue result : results) {
            Model model = genson.deserialize(result.getStringValue(), Model.class);
            System.out.println(model);
            queryResults.add(model);
        }

        return queryResults;
    }


    /**
     * Checks the existence of the task on the ledger
     *
     * @param ctx the transaction context
     * @return boolean indicating the existence of the task
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public boolean AssetExists(final Context ctx, final String t) {
        ChaincodeStub stub = ctx.getStub();
        String modelJSON = stub.getStringState(t);
        return (modelJSON != null && !modelJSON.isEmpty());
    }

    /**
     * 解析包含整数的字符串列表
     *
     * @param str       字符串，格式为"1,2,3,4,5"
     * @param delimiter 分隔符，默认为逗号
     * @return 整数列表
     */
    public static List<Integer> parseStringListToIntList(String str, String delimiter) {
        List<Integer> intList = new ArrayList<>();

        // 使用指定的分隔符分割字符串
        String[] strArray = str.split(delimiter);

        // 遍历字符串数组，将每个字符串转换为整数并添加到列表中
        for (String s : strArray) {
            try {
                intList.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
                // 如果转换失败，可以添加错误处理逻辑，这里简单忽略
                System.err.println("无法将字符串 '" + s + "' 转换为整数");
            }
        }

        return intList;
    }

    // 计算列表中所有元素的和
    private static int sum(List<Integer> list) {
        int total = 0;
        for (int num : list) {
            total += num;
        }
        return total;
    }

    // 聚合模型和权重
    private static Map<String, List<List<Double>>> aggregate(List<Double> weights, List<Model> models) {
        Double sum = weights.stream().mapToDouble(i -> i).sum();
        // 存储计算后的模型
        List<Map<String, List<List<Double>>>> caculate_param = new ArrayList<>();
        // 遍历模型和权重
        for (int i = 0; i < models.size(); i++) {
            Model cu_model = models.get(i);
            double weight = weights.get(i);

            Map<String, List<List<Double>>> params = parseModelFeatures(cu_model.getParam());
            // 根据权重重新计算特征
            params.forEach((k, v) ->
                    v.forEach(list -> list.forEach(p -> {
                        p = (weight / sum) * p;
                    })));
            caculate_param.add(params);
        }
        // 聚合所有计算后的模型
        Map<String, List<List<Double>>> resultParams = new HashMap<>();
        resultParams = caculate_param.get(0);
        caculate_param.remove(0);
        //遍历属性
        for (String key : resultParams.keySet()) {
            List<List<Double>> list = resultParams.get(key);
            for (int i = 1; i < caculate_param.size(); i++) {
                Map<String, List<List<Double>>> cu_params = caculate_param.get(i);
                // 若当前模型不包含该属性，则跳过
                if (!cu_params.containsKey(key)) {
                    continue;
                }
                // 获取当前模型的特征列表
                List<List<Double>> cu_list = cu_params.get(key);
                //将两个列表对应位置的元素相加
                for (int j = 0; j < list.size(); j++) {
                    List<Double> cu_sub = cu_list.get(j);
                    if (cu_sub == null) {
                        continue;
                    }
                    for (int k = 0; k < cu_sub.size(); k++) {
                        double value = list.get(j).get(k) + cu_sub.get(k);
                        list.get(j).set(k, value);
                    }
                }
            }
        }
        return resultParams;
    }

    //解析模型特征
    private static Map<String, List<List<Double>>> parseModelFeatures(String param) {
        ObjectMapper objectMapper = new ObjectMapper();
        // 存储解析结果
        Map<String, List<List<Double>>> resultMap = new HashMap<>();
        // 解析JSON字符串为JsonNode
        try {
            JsonNode rootNode = objectMapper.readTree(param);
            // 遍历param的所有字段
            Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode valueNode = field.getValue();

                // 确保值是一个数组
                if (valueNode.isArray()) {
                    List<List<Double>> listOfLists = null;
                    try {
                        listOfLists = objectMapper.readValue(valueNode.traverse(), new TypeReference<List<List<Double>>>() {});
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    resultMap.put(key, listOfLists);
                }
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return resultMap;
    }



}
