package cn.gson.oasys.controller.login;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.util.StringUtil;
import com.github.stuxuhai.jpinyin.PinyinFormat;
import com.github.stuxuhai.jpinyin.PinyinHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import cn.gson.oasys.model.dao.user.UserDao;
import cn.gson.oasys.model.entity.user.LoginRecord;
import cn.gson.oasys.model.entity.user.User;
import cn.gson.oasys.services.user.UserLongRecordService;
import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.UserAgent;
import eu.bitwalker.useragentutils.Version;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;


@Controller
@RequestMapping("/")
public class LoginsController {

    @Autowired
    private UserDao uDao;
    @Autowired
    UserLongRecordService ulService;

    public static final String CAPTCHA_KEY = "session_captcha";

    private Random rnd = new Random();

    /**
     * 登录界面的显示
     *
     * @return
     */
    @RequestMapping(value = "logins", method = RequestMethod.GET)
    public String login() {
        return "login/login";
    }

    @RequestMapping("loginout")
    public String loginout(HttpSession session) {
        session.removeAttribute("userId");
        return "redirect:/logins";
    }

    /**
     * 登录检查；
     * 1、根据(用户名或电话号码)+密码进行查找
     * 2、判断使用是否被冻结；
     *
     * @return
     * @throws UnknownHostException
     */
    @RequestMapping(value = "logins", method = RequestMethod.POST)
    public String loginCheck(HttpSession session, HttpServletRequest req, Model model) throws UnknownHostException {
        String userName = req.getParameter("userName").trim();
        String password = req.getParameter("password");
        String ca = req.getParameter("code").toLowerCase();
        String sesionCode = (String) req.getSession().getAttribute(CAPTCHA_KEY);
        model.addAttribute("userName", userName);
        if (!ca.equals(sesionCode.toLowerCase())) {
            System.out.println("验证码输入错误!");
            model.addAttribute("errormess", "验证码输入错误!");
            req.setAttribute("errormess", "验证码输入错误!");
            return "login/login";
        }
        /*
         * 将用户名分开查找；用户名或者电话号码；
         * */
        User user = uDao.findOneUser(userName, password);
        if (Objects.isNull(user)) {
            System.out.println(user);
            System.out.println("账号或密码错误!");
            model.addAttribute("errormess", "账号或密码错误!");
            return "login/login";
        }
        System.out.println("是否被锁：" + user.getIsLock());
        if (user.getIsLock() == 1) {
            System.out.println("账号已被冻结!");
            model.addAttribute("errormess", "账号已被冻结!");
            return "login/login";
        }
        Object sessionId = session.getAttribute("userId");
        System.out.println(user);
        if (sessionId == user.getUserId()) {
            System.out.println("当前用户已经登录了；不能重复登录");
            model.addAttribute("hasmess", "当前用户已经登录了；不能重复登录");
            session.setAttribute("thisuser", user);
            return "login/login";
        } else {
            session.setAttribute("userId", user.getUserId());
            Browser browser = UserAgent.parseUserAgentString(req.getHeader("User-Agent")).getBrowser();
            Version version = browser.getVersion(req.getHeader("User-Agent"));
            String info = browser.getName() + "/" + version.getVersion();
            String ip = InetAddress.getLocalHost().getHostAddress();
            /*新增登录记录*/
            ulService.save(new LoginRecord(ip, new Date(), info, user));
        }
        return "redirect:/index";
    }

    @RequestMapping("handlehas")
    public String handleHas(HttpSession session) {
        if (!StringUtils.isEmpty(session.getAttribute("thisuser"))) {
            User user = (User) session.getAttribute("thisuser");
            System.out.println(user);
            session.removeAttribute("userId");
            session.setAttribute("userId", user.getUserId());
        } else {
            System.out.println("有问题！");
            return "login/login";
        }
        return "redirect:/index";

    }


    @RequestMapping("captcha")
    public void captcha(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException {
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setContentType("image/jpeg");

        // 生成随机字串
        String verifyCode = VerifyCodeUtils.generateVerifyCode(4);

        // 生成图片
        int w = 135, h = 40;
        VerifyCodeUtils.outputImage(w, h, response.getOutputStream(), verifyCode);

        // 将验证码存储在session以便登录时校验
        session.setAttribute(CAPTCHA_KEY, verifyCode.toLowerCase());
    }

    //移动端验证
    @RequestMapping(value = "mobileCheck", method = RequestMethod.POST)
    @ResponseBody
    public JSONObject mobileCheck(@RequestParam("filePath") MultipartFile orignFile, @RequestParam("idCard") String idCard) throws IOException {
        System.out.println("移动端验证：" + idCard);
        // 获取文件名
        String fileName = orignFile.getOriginalFilename();
        // 获取文件后缀
        String prefix = fileName.substring(fileName.lastIndexOf("."));
        // 用uuid作为文件名，防止生成的临时文件重复
//		final File imgFile = File.createTempFile(UUID.randomUUID().toString(), prefix);
		final File imgFile = File.createTempFile(idCard,prefix);
        // MultipartFile to File
        orignFile.transferTo(imgFile);
        JSONObject status = new JSONObject();
        status.put("code", 1);
        status.put("msg", "验证失败");
        JSONObject data = sendInfo(imgFile);
        if (Integer.parseInt(data.get("code").toString()) == 0) {
            status.put("code", 0);
            status.put("msg", "验证通过");
        }
        return status;
    }

    private static JSONObject sendInfo(File imgFile) {
        final String url = "http://127.0.0.1:8088/testPost";
        RestTemplate restTemplate = new RestTemplate();
        //设置请求头
        HttpHeaders headers = new HttpHeaders();
        MediaType type = MediaType.parseMediaType("multipart/form-data");
        headers.setContentType(type);
        //设置请求体，注意是LinkedMultiValueMap
        FileSystemResource fileSystemResource = new FileSystemResource(imgFile);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("code", 0);
        form.add("id_num", fileSystemResource);
        form.add("msg", "success");
        //用HttpEntity封装整个请求报文
        HttpEntity<MultiValueMap<String, Object>> files = new HttpEntity<>(form, headers);
        return restTemplate.postForObject(url, files, JSONObject.class);
    }




}
