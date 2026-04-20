package com.smarttest.manager;

import com.smarttest.manager.service.DocDownloadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class ManagerServiceMdMockTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocDownloadService docDownloadService;

    @Test
    public void testCreateWithMockedMd() throws Exception {
        // 直接传入 md 内容，跳过 docsdownload 接口调用
        String mdContent = "***\n\n" +
                "## protocol: DUBBO/HTTP\n\n" +
                "## 需求描述：\n\n" +
                "### 需求场景\n\n" +
                "新增通过员工姓名匹配客户信息功能。\n\n" +
                "### 测试要求\n\n" +
                "1，只需按照需求场景测试必要功能，前置能力无需验证\n" +
                "2，无需对dubbo接口基本信息中的内容进行验证，严格按照接口定义进行调用即可。\n\n" +
                "## 接口定义\n\n" +
                "### 根据工号查询员工信息接口\n\n" +
                "**接口基本信息**：\n\n" +
                "| 属性      | 值                                          |\n" +
                "| :------ | :----------------------------------------- |\n" +
                "| dubbo接口 | com.htsc.pwm.cust.service.CommonEmpService |\n" +
                "| dubbo方法 | getCurrentEmpInfo(java.lang.String)        |\n" +
                "| 接口版本    | 0.0.1                                      |\n\n" +
                "**输入示例**\n\"xxxxx\"\n\n" +
                "### 通过员工姓名匹配客户信息接口\n\n" +
                "**接口基本信息**：\n\n" +
                "| 属性      | 值                                                                 |\n" +
                "| :------ | :---------------------------------------------------------------- |\n" +
                "| dubbo接口 | com.htsc.pwm.cust.service.DigitalEmployeeService                  |\n" +
                "| dubbo方法 | matchCustByName(com.htsc.pwm.cust.dto.cust.MatchCustByNameReqDTO) |\n" +
                "| 接口版本    | 0.0.1                                                             |\n\n" +
                "**输入参数MatchCustByNameReqDTO明细**\n\n" +
                "| 字段           | 类型           | 是否可为空 | 说明        |\n" +
                "| :----------- | :----------- | :---- | :-------- |\n" +
                "| custNameList | List<String> | 否     | list最长为10 |\n\n" +
                "**输入示例**\n" +
                "{\"custNameList\":[\"xxxxx\"]}\n\n" +
                "**输出信息**\n\n" +
                "| 字段         | 类型                                               | 是否可为空 | 说明      |\n" +
                "| :--------- | :----------------------------------------------- | :---- | :------ |\n" +
                "| resultData | com.htsc.pwm.cust.dto.cust.MatchCustByNameResDTO | 否     | 客户信息    |\n" +
                "| code       | String                                           | 否     | 为0时代表成功 |\n\n" +
                "**MatchCustByNameResDTO明细**\n\n" +
                "| 字段                | 类型                                              | 是否可为空 | 说明   |\n" +
                "| :---------------- | :---------------------------------------------- | :---- | :--- |\n" +
                "| matchCustInfoList | List\\<com.htsc.pwm.cust.dto.cust.MatchCustInfo> | 否     | 客户信息 |\n\n" +
                "**MatchCustInfo明细**\n\n" +
                "| 字段          | 类型     | 是否可为空 | 说明                  |\n" +
                "| :---------- | :----- | :---- | :------------------ |\n" +
                "| custId      | String | 否     | 客户id                |\n" +
                "| custName    | String | 否     | 客户姓名                |\n" +
                "| custType    | String | 否     | 客户类型（1-正客，2-潜客）     |\n" +
                "| custClass   | String | 否     | 组织类型（PER-个人，ORG-组织） |\n" +
                "| workCompany | String | 否     | 就职单位                |\n" +
                "| creditcode  | String | 否     | 信用代码                |\n" +
                "| mngLogin    | String | 否     | 服务经理                |\n" +
                "| deptCode    | String | 否     | 部门编码                |\n\n" +
                "## 测试环境\n\n" +
                "**ZK注册地址**：zookeeper://168.63.65.196:2182?backup=168.63.65.197:2182,168.63.65.198:2182\n\n" +
                "## 测试数据\n\n" +
                "员工工号：023023\n";

        when(docDownloadService.downloadDoc(anyString(), anyString(), anyString())).thenReturn(mdContent);

        String requestJson = "{\n" +
                "  \"storyid\": \"STORY-001\",\n" +
                "  \"phase\": \"SIT\",\n" +
                "  \"doctype\": \"api\",\n" +
                "  \"testPointIds\": [\"TP001\"],\n" +
                "  \"environment\": \"## 测试环境\\n\\n**ZK注册地址**：zookeeper://168.63.65.196:2182\",\n" +
                "  \"callbackUrl\": \"http://localhost:9999/callback\"\n" +
                "}";

        mockMvc.perform(post("/api/v1/manager/tasks/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.managerTaskId").exists())
                .andExpect(jsonPath("$.status").value("dispatched"))
                .andExpect(jsonPath("$.createdAt").exists());
    }
}
