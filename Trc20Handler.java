package com.sjh;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sjh.tron.HttpClientUtils;
import com.sjh.tron.TransformUtil;
import com.sjh.tron.TronUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class Trc20Handler {

    private TrxHandler trxHandler;

    //主网
    private static final String tronUrl = "https://api.trongrid.io";

    private static Logger logger = LoggerFactory.getLogger(Trc20Handler.class);

    public Trc20Handler() {
    }

    /**
     * 合约精度
     */
    private BigDecimal decimal = new BigDecimal("1000000");

    /**
     * trc20合约地址 这个是USDT代币，如果需要别的，自己替换就好
     */
    private String contract = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

    public static final String USDT_CPNTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

    private static BigInteger TRC20_TARGET_BLOCK_NUMBER;
    private static BigInteger CURRENT_SYNC_BLOCK_NUMBER;
    private static BigInteger BLOCKS = new BigInteger("15");

    @EventListener(ApplicationReadyEvent.class)
    public void init() throws Throwable {
		//从最新区块减去指定区块开始(为防止扫描超过链上高度)
        CURRENT_SYNC_BLOCK_NUMBER = getNowBlock().subtract(BLOCKS);
        //   从指定区块扫描
        // CURRENT_SYNC_BLOCK_NUMBER = new BigInteger("1");
    }


    /**
     * 查询trc20数量
     */
    public BigDecimal balanceOfTrc20(String address, String contract) {
        String url = tronUrl + "/wallet/triggerconstantcontract";
        JSONObject param = new JSONObject();
        param.put("owner_address", TronUtils.toHexAddress(address));
        param.put("contract_address", TronUtils.toHexAddress(contract));
        param.put("function_selector", "balanceOf(address)");
        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Address(TronUtils.toHexAddress(address).substring(2)));
        param.put("parameter", FunctionEncoder.encodeConstructor(inputParameters));
        String result = HttpClientUtils.postJson(url, param.toJSONString());
        if (StringUtils.isNotEmpty(result)) {
            JSONObject obj = JSONObject.parseObject(result);
            JSONArray results = obj.getJSONArray("constant_result");
            if (results != null && results.size() > 0) {
                BigInteger amount = new BigInteger(results.getString(0), 16);
                return new BigDecimal(amount).divide(decimal, 6, RoundingMode.FLOOR);
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 创建地址(明文私钥)
     */
    public void createAddress() {
        Map<String , String> addressMap = TronUtils.createAddress();
        for (Map.Entry<String , String> entrySet : addressMap.entrySet()) {
            String key = entrySet.getKey();
            String value = entrySet.getValue();
            System.out.println("key" + key + "，value" + value);
        }

    }

    public void findAimAddress(){

    }


    /**
     * 获取特定区块的所有交易信息
     *
     * @return
     */
    private String getTransactionInfoByBlockNum(BigInteger num) {
        String url = tronUrl + "/wallet/gettransactioninfobyblocknum";
        Map<String, Object> map = new HashMap<>();
        map.put("num", num);
        String param = JSON.toJSONString(map);
        return HttpClientUtils.postJson(url, param);
    }

    /**
     * 发起trc20转账 (目标地址,数量,合约地址,私钥)
     * 地址 默认为usdt 合约地址
     * @throws Throwable
     */
    public synchronized String sendTrc20(String toAddress, BigDecimal amount, String privateKey) throws Throwable {
        String ownerAddress = TronUtils.getAddressByPrivateKey(privateKey);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("contract_address", TronUtils.toHexAddress(USDT_CPNTRACT));
        jsonObject.put("function_selector", "transfer(address,uint256)");
        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Address(TronUtils.toHexAddress(toAddress).substring(2)));
        inputParameters.add(new Uint256(amount.multiply(decimal).toBigInteger()));
        String parameter = FunctionEncoder.encodeConstructor(inputParameters);
        jsonObject.put("parameter", parameter);
        jsonObject.put("owner_address", TronUtils.toHexAddress(ownerAddress));
        jsonObject.put("call_value", 0);
        jsonObject.put("fee_limit", 6000000L);
        String trans1 = HttpClientUtils.postJson(tronUrl + "/wallet/triggersmartcontract", jsonObject.toString());
        JSONObject result = JSONObject.parseObject(trans1);
        System.out.println("trc20 result:" + result.toJSONString());
        if (result.containsKey("Error")) {
            throw new RuntimeException("result.containsKey(\"Error\")");
        }
        JSONObject tx = result.getJSONObject("transaction");
        //填写备注
        tx.getJSONObject("raw_data").put("data", Hex.toHexString("备注信息".getBytes()));
        String txid = TronUtils.signAndBroadcast(tronUrl, privateKey, tx);
        if (txid != null) {
            System.out.println("txid:" + txid);
            return txid;
        }
        return null;
    }

	
	/**
     * 归集，自定义执行时间
     *
     * @throws Throwable
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void charge() throws Throwable {
        if (CURRENT_SYNC_BLOCK_NUMBER != null) {
            TRC20_TARGET_BLOCK_NUMBER = getNowBlock().subtract(BLOCKS);
            if (CURRENT_SYNC_BLOCK_NUMBER.compareTo(TRC20_TARGET_BLOCK_NUMBER) <= 0) {
                String transactionInfoByBlockNum = getTransactionInfoByBlockNum(CURRENT_SYNC_BLOCK_NUMBER);
                JSONArray parseArray = JSON.parseArray(transactionInfoByBlockNum);
                logger.info("当前同步TRC20区块:" + CURRENT_SYNC_BLOCK_NUMBER + ",txs:" + parseArray.size());
                if (parseArray.size() > 0) {
                    parseArray.forEach(e -> {
                        try {
                            String txId = JSON.parseObject(e.toString()).getString("id");
                            JSONObject parseObject = JSON.parseObject(getTransactionById(txId));
                            String contractRet = parseObject.getJSONArray("ret").getJSONObject(0).getString("contractRet");
                            //交易成功
                            if ("SUCCESS".equalsIgnoreCase(contractRet)) {
                                String type = parseObject.getJSONObject("raw_data").getJSONArray("contract").getJSONObject(0).getString("type");
                                if ("TriggerSmartContract".equalsIgnoreCase(type)) {
                                    //合约地址转账
                                    triggerSmartContract(parseObject, txId);
                                } else if ("TransferContract".equalsIgnoreCase(type)) {
                                    //trx 转账
                                    transferContract(parseObject);
                                }
                            }
                        } catch (Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    });
                }
                CURRENT_SYNC_BLOCK_NUMBER = CURRENT_SYNC_BLOCK_NUMBER.add(BigInteger.ONE);
                charge();
            }
        } else {
            init();
        }
    }
	
	/**
     * 扫描TRX交易
     *
     * @throws Throwable
     */
    private synchronized void transferContract(JSONObject parseObject) throws Throwable {
        JSONObject jsonObject = parseObject.getJSONObject("raw_data").getJSONArray("contract").getJSONObject(0).getJSONObject("parameter").getJSONObject("value");
        //调用者地址
        String ownerAddress = jsonObject.getString("owner_address");
        ownerAddress = TronUtils.toViewAddress(ownerAddress);
        //转入地址
        String toAddress = jsonObject.getString("to_address");
        toAddress = TronUtils.toViewAddress(toAddress);
        String address = "归集地址";
        if (ownerAddress.equalsIgnoreCase(address)) {
            //查看USDT余额是否大于0
            BigDecimal bigDecimal = balanceOfTrc20(toAddress, "合约地址");
            if (bigDecimal.compareTo(BigDecimal.ZERO) > 0) {
                //确定用户充值，归集
                String pk = "给用户send TRX的私钥";
                String hash = sendTrc20(address, bigDecimal,  "私钥");
            }

        }

    }

	/**
     * 扫描合约地址交易
     *
     * @throws Throwable
     */
    private synchronized void triggerSmartContract(JSONObject parseObject, String txId) throws Throwable {
        //方法参数
        JSONObject jsonObject = parseObject.getJSONObject("raw_data").getJSONArray("contract").getJSONObject(0).getJSONObject("parameter").getJSONObject("value");
        // 合约地址
        String contractAddress = jsonObject.getString("contract_address");
        contractAddress = TronUtils.toViewAddress(contractAddress);
        String data = jsonObject.getString("data").substring(8);
        List<String> strList = TransformUtil.getStrList(data, 64);
        if (strList.size() != 2) {
            return;
        }
        String toAddress = TransformUtil.delZeroForNum(strList.get(0));
        if (!toAddress.startsWith("41")) {
            toAddress = "41" + toAddress;
        }
        //收款地址
        toAddress = TronUtils.toViewAddress(toAddress);
        //相匹配的合约地址
        if (!contract.equalsIgnoreCase(contractAddress)) {
            return;
        }
        // TODO: 2021/9/7 可以增加自己的判断
        String amountStr = TransformUtil.delZeroForNum(strList.get(1));
        if (amountStr.length() > 0) {
            amountStr = new BigInteger(amountStr, 16).toString(10);
        }
        //amount 是充币的数量，自己操作
        BigDecimal amount = BigDecimal.ZERO;
        if (StringUtils.isNotEmpty(amountStr)) {
            amount = new BigDecimal(amountStr).divide(decimal, 6, RoundingMode.FLOOR);
        }
        //具体流程操作
		//打TRX手续费,以便归集时使用。
        String hash = trxHandler.sendTrx(new BigDecimal("2.5"), toAddress, "归集手续费");
        logger.info("给 " + toAddress + "发送手续费，交易hash：" + hash);
    }

    /**
     * 查询最新区块
     *
     * @return
     */
    public BigInteger getNowBlock() {
        String url = tronUrl + "/wallet/getnowblock";
        String httpRequest = HttpRequest.get(url).execute().body();
        JSONObject jsonObject1 = JSONObject.parseObject(httpRequest);
        return jsonObject1.getJSONObject("block_header").getJSONObject("raw_data").getBigInteger("number");
    }


    /**
     * 返回交易状态
     */
    public boolean transactionStatus(String hash) {
        JSONObject parseObject = JSON.parseObject(getTransactionById(hash));
        if (StringUtils.isEmpty(parseObject.toJSONString())) {
            return false;
        }
        String contractRet = parseObject.getJSONArray("ret").getJSONObject(0).getString("contractRet");
        return "SUCCESS".equals(contractRet);
    }

    /**
     * 通过HASH获取交易信息
     *
     * @param hash
     * @return
     */
    public String getTransactionById(String hash) {
        String url = tronUrl + "/walletsolidity/gettransactionbyid";
        Map<String, Object> map = new HashMap<>();
        map.put("value", hash);
        String param = JSON.toJSONString(map);
        return HttpClientUtils.postJson(url, param);
    }

    @Autowired
    public void setTrxHandler(TrxHandler trxHandler) {
        this.trxHandler = trxHandler;
    }




}
