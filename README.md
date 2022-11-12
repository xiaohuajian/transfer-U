# trc20-usdt
java 实现的trc20-usdt

### 前言

最近因项目使用tron 协议接入区块链，故对其做了一番研究，先把相关资料整理一遍，供大家学习使用；

网上的这部分资料很少，所以学习起来也是遇到了很多困难，尤其是里面很多新的概念，理解起来有一定的难度。比如说去中心化、地址、加密算法、算法因子、私钥含义、助记词、trc协议、智能合约、usdt等等；

很多人接触区块链，大多是通过接触usdt这种中充当**稳定资产**（也称泰达token）角色开始的，usdt是什么，

是一种基本衡量单位，即稳定资产usdt，是usdt背后发行者公司每发行一枚usdt 就要往对应的银行机构存入对应的法定资产，这样然后usdt稳定资产发行者通过存入三方银行机构法定资产，来保证我的usdt是有保证的，不会超发或者失去赔付能力；

介绍了前面这些背景，再说说其他的，其实 token 后面的底层技术区块链有很多很多的知识点，就不一一细说了，比如这些token是怎么发行的，如何运转的，如何做到去中心化的；这里主要讲讲大家经常使用trc20 交易usdt实现和背后的含义；

> 要明白trc20是一种协议，这个协议是波场tron链下面的一种，还有trx，trc10，trc721等等，而波场链跟usdt 发行者公司合作，写了一份智能合约，该协议实现了几种功能，如交易、查询、授权、事件监听等等，我们在地址中转账看到的trc20-usdt 就是执行了这个交易方法 **transfer**，所以能够把一个地址中的usdt转移到另一个地址；

```
trc20 协议中支持的方法
contract TRC20 {
    function totalSupply() constant returns (uint theTotalSupply);
    function balanceOf(address _owner) constant returns (uint balance);
    function transfer(address _to, uint _value) returns (bool success);
    function transferFrom(address _from, address _to, uint _value) returns (bool success);
    function approve(address _spender, uint _value) returns (bool success);
    function allowance(address _owner, address _spender) constant returns (uint remaining);
    event Transfer(address indexed _from, address indexed _to, uint _value);
    event Approval(address indexed _owner, address indexed _spender, uint _value);
}
```



### Trc20-usdt

要实现交易，首先要得有地址，自己地址，对方地址，usdt，trx 燃料费；然后这几个要素经过什么步骤才能达到目的？

**创建交易 、离线签名、广播**

先把代码贴出来：

```java
/**
     * 发起trc20交易 (目标地址,数量,合约地址,私钥)
     * 地址 默认为usdt 合约地址
     * @throws Throwable
     */
    public String sendTrc20(String toAddress, BigDecimal amount, String privateKey) throws Throwable {
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
```



### 创建交易

```java
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
```



先通过私钥获取自己的地址，然后指定合约地址，即usdt 在波场的合约地址，指定合约中的方法；然后指定对方地址、附上燃料费trx ，通过调用 `/wallet/triggersmartcontract` 创建交易；至此第一步就算完成了；这里需要说明，trx 燃料费的概念，也就是支付给区块链节点的矿工费用；如果没有trx 交易是不成功的；很多人疑惑，为啥用交易所不需要trx，那是因为交易所帮你给付了，用web3 wallet 转，必须支付trx；



### 签名和广播

```java
public static String signAndBroadcast(String tronUrl,String privateKey,JSONObject transaction)throws Throwable{
		if(tronUrl.endsWith("/")){
			tronUrl= tronUrl.substring(0,tronUrl.length() - 1);
		}
		Protocol.Transaction tx = packTransaction(transaction.toJSONString());
		byte[] bytes = signTransactionByte(tx.toByteArray(), ByteArray.fromHexString(privateKey));
		String signTransation = Hex.toHexString(bytes);
		JSONObject jsonObjectGB = new JSONObject();
		jsonObjectGB.put("transaction", signTransation);
		String url = tronUrl + "/wallet/broadcasthex";
		String transationCompelet1 = HttpClientUtils.postJson(url, jsonObjectGB.toString());
		JSONObject transationCompelet = JSONObject.parseObject(transationCompelet1);
		System.out.println("signAndBroadcast transationCompelet:" + transationCompelet.toJSONString());
		if (transationCompelet.getBoolean("result")) {
			return transationCompelet.getString("txid");
		} else {
			logger.error(String.format("签名交易失败:%s",transationCompelet1));
			return null;
		}
	}

	/**
	 * 签名交易
	 * @param transaction
	 * @param privateKey
	 * @return
	 * @throws InvalidProtocolBufferException
	 * @throws NoSuchAlgorithmException
	 */
	public static byte[] signTransactionByte(byte[] transaction, byte[] privateKey) throws InvalidProtocolBufferException, NoSuchAlgorithmException {
		ECKey ecKey = ECKey.fromPrivate(privateKey);
		Protocol.Transaction transaction1 = Protocol.Transaction.parseFrom(transaction);
		byte[] rawdata = transaction1.getRawData().toByteArray();
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(rawdata,0,rawdata.length);
		byte[] hash= digest.digest();
		byte[] sign = ecKey.sign(hash).toByteArray();
		return transaction1.toBuilder().addSignature(ByteString.copyFrom(sign)).build().toByteArray();
	}
```



#### 名验证的原理

在已知交易发起者（contract owner）地址的情况下，通过签名消息逆推公钥（recover），并将公钥转换为地址，与发起者地址进行比较。如果地址一致，即为验证成功。

#### 验证签名的方法

验证方法需要三个参数：

- 交易id（即交易哈希，通过`Transaction.rawData`计算SHA256得到）
- 签名消息（即`Transaction.signature`）
- 发起者地址（即`Transaction.rawData.contract.parameter.ownerAddress`，其中`parameter`的类型是`com.google.protobuf.Any`，需要根据具体交易类型来进行`unpack`操作）

```java
byte[] bytes = signTransactionByte(tx.toByteArray(), ByteArray.fromHexString(privateKey));
String signTransation = Hex.toHexString(bytes);
```



### 广播 

广播可理解为发送交易。任何与波场网络的交互行为都被称作为一笔交易。一笔交易可以是TRX转账、质押/解锁TRX、触发智能合约等。

**只有消耗资源的交易才会被记录在链上。** 前面提到了trx 燃料费，就是这里的消耗的资源；当区块链的其他节点确认了你的交易，并把此笔交易广播给其他人后，这笔交易就算交易成功，即同步到其他节点的数据库了；

`wrapper.broadcastTransaction(signedTransaction); //return transaction hash if successfully broadcasted, otherwise the error code`

```java
String url = tronUrl + "/wallet/broadcasthex";
String transationCompelet1 = HttpClientUtils.postJson(url, jsonObjectGB.toString());
JSONObject transationCompelet = JSONObject.parseObject(transationCompelet1);
```



以上就是trc20-usdt 转账的背后逻辑。下面讲讲wallet地址以及wallet地址的创建和生成；



### wallet

wallet可以理解为加密算法中配对的公钥和私钥；tron wallet采用的加密算法是 波场的签名算法是ECDSA，选用的曲线是SECP256K1。

在使用web3 wallet时，经常会让我们主动创建或导入住记词、私钥的方式创建wallet，这后面的原理又是什么呢？

#### **wallet地址**

我们可以理解wallet地址是这套算法中公钥，这个地址是公开的，别人可以向你进行交易等等；而经常说的助记词就是把私钥经过==可逆算法==转换成了12个常见的英文字符串，二者是等价的（这个过程和产生wallet地址、私钥算法不一样），明白加密算法的人都知道，加密算法一般不具备可逆向性的，私钥能推导出公钥的，反之不行。**所以务必保护好你的私钥及代表私钥的助记词。**

好了，明白这些东西后，那我们看代码：

```java
	/**
	 * 离线创建地址
	 *
	 * @return
	 */
	public static Map<String, String> createAddress() {
		ECKey eCkey = new ECKey(random);
    String privateKey = ByteArray.toHexString(eCkey.getPrivKeyBytes());
		byte[] addressBytes = eCkey.getAddress();
		String hexAddress = ByteArray.toHexString(addressBytes);
		Map<String, String> addressInfo = new HashMap<>(3);
		addressInfo.put("address", toViewAddress(hexAddress));
		addressInfo.put("hexAddress", hexAddress);
		addressInfo.put("privateKey", privateKey);
		return addressInfo;
	}
```

在这个过程中，涉及到了大量的算法相关的知识，需要说明的是tron wallet的加密算法经过多次转换和加密的，这个过程非常之复杂，就不展开讲了。



### 地址查询

如果我们知道了一个wallet地址，我们可以查询其wallet的交易情况，比如tron 链上的所有协议，如trx交易、trc20-usdt 交易等等；

```java
 String specificWalletTransferUrl = urlAddress + blockWalletBean.monitorAddress + "/transactions/trc20";

Map<String, String> paraMap = new HashMap<>();
paraMap.put("limit", "30");
paraMap.put("only_confirmed", "true");
paraMap.put("contract_address", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
String content = httpGet(specificWalletTransferUrl, paraMap);
System.out.println("content:" + content);
f (!StringUtils.isEmpty(content)) {
                 
    JSONObject jsonObject = JSONObject.parseObject(content);
    JSONArray results = jsonObject.getJSONArray("data");
    //解析数据，获取wallet address交易详细信息
  
```

 

### 区块扫描

```java
   public BigInteger getNowBlock() {
        String url = tronUrl + "/wallet/getnowblock";
        String httpRequest = HttpRequest.get(url).execute().body();
        JSONObject jsonObject1 = JSONObject.parseObject(httpRequest);
        return jsonObject1.getJSONObject("block_header").getJSONObject("raw_data").getBigInteger("number");
    }
```



### 写在最后

其实这个wallet 、智能合约还有很多的功能，我们经常听到有些人的被盗，那些被盗的人怎么做到的呢，我们该如何去防范呢？这些东西需要我们深入研究才能明白其中的奥秘，好了篇幅有限，至此结束。

[文章链接](https://zhuanlan.zhihu.com/p/582849233) 
------

### 总结

谢谢你的观看，觉得对你有用的话，点个赞，不胜感激。

想要深入交流的可以加V ：xyxiaohuajian ，简单备注（trc20）；
