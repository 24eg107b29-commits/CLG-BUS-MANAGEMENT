import java.util.*;
public class TestJson {
    public static void main(String[] args) {
        Map<String, Object> obj = JsonUtil.parseObject("{\"name\":\"Alice\",\"msg\":\"line\\nnext\",\"items\":[1,true,null,{\"x\":\"y\"}]}");
        System.out.println(obj);
        System.out.println(obj.get("msg"));
        System.out.println(((List<Object>) obj.get("items")).get(3));
    }
}
