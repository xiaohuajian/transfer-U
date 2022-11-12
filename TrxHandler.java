package com.sjh;

import com.alibaba.fastjson.JSONObject;
import com.sjh.tron.HttpClientUtils;
import com.sjh.tron.TronUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.TimeoutException;

@Service
public class TrxHandler {

    //主网
    private static final String tronUrl = "https://api.trongrid.io";

    /**
     * trx精度  1 trx = 1000000 sun
     */
    private BigDecimal decimal = new BigDecimal("1000000");

    public boolean isEmpty(String content){
        if (content == null || content.length() == 0){
            return true;
        }
        return false;
    }

    /**
     * TRX转账
     */
    public String sendTrx(BigDecimal amount,String toAddress,String note) throws Throwable {
        String url = tronUrl + "/wallet/createtransaction";
        JSONObject param = new JSONObject();
        String privateKey = "私钥";
        param.put("owner_address", TronUtils.toHexAddress(TronUtils.getAddressByPrivateKey(privateKey)));
        param.put("to_address",TronUtils.toHexAddress(toAddress));
        param.put("amount",amount.multiply(decimal).toBigInteger());
        String result = HttpClientUtils.postJson(url, param.toJSONString());
        if(StringUtils.isNotEmpty(result)){
            JSONObject transaction = JSONObject.parseObject(result);
            String error = transaction.getString("Error");
            if(!isEmpty(error) && error.contains("balance is not sufficient")){
                //抛自己的异常(余额不足)
                System.out.println("balance is not sufficient");
                throw new TimeoutException("balance is not sufficient");
            }else {
                transaction.getJSONObject("raw_data").put("data", Hex.toHexString(note.getBytes()));
                return TronUtils.signAndBroadcast(tronUrl, privateKey, transaction);
            }
        }
        //抛自己的异常(超时)
        throw new TimeoutException();
    }

    /**
     * 查询TRX余额
     */
    public BigDecimal balanceOf(String address) {
        String url = tronUrl + "/wallet/getaccount";
        JSONObject param = new JSONObject();
        param.put("address", TronUtils.toHexAddress(address));
        String result = HttpClientUtils.postJson(url, param.toJSONString());
        BigInteger balance = BigInteger.ZERO;
        if (!StringUtils.isEmpty(result)) {
            JSONObject obj = JSONObject.parseObject(result);
            BigInteger b = obj.getBigInteger("balance");
            if(b != null){
                balance = b;
            }
        }
        return new BigDecimal(balance).divide(decimal,6, RoundingMode.FLOOR);
    }
}
