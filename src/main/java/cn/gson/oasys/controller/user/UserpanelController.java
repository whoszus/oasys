package cn.gson.oasys.controller.user;

import cn.gson.oasys.common.formValid.BindingResultVOUtil;
import cn.gson.oasys.common.formValid.MapToList;
import cn.gson.oasys.common.formValid.ResultEnum;
import cn.gson.oasys.common.formValid.ResultVO;
import cn.gson.oasys.model.dao.informdao.InformRelationDao;
import cn.gson.oasys.model.dao.maildao.MailreciverDao;
import cn.gson.oasys.model.dao.processdao.NotepaperDao;
import cn.gson.oasys.model.dao.user.DeptDao;
import cn.gson.oasys.model.dao.user.PositionDao;
import cn.gson.oasys.model.dao.user.UserDao;
import cn.gson.oasys.model.entity.mail.Mailreciver;
import cn.gson.oasys.model.entity.notice.NoticeUserRelation;
import cn.gson.oasys.model.entity.process.Notepaper;
import cn.gson.oasys.model.entity.user.User;
import cn.gson.oasys.services.user.NotepaperService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.util.StringUtil;
import com.github.stuxuhai.jpinyin.PinyinException;
import com.github.stuxuhai.jpinyin.PinyinFormat;
import com.github.stuxuhai.jpinyin.PinyinHelper;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ResourceUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/")
public class UserpanelController {
    @Autowired
    private UserDao udao;

    @Autowired
    private DeptDao ddao;
    @Autowired
    private PositionDao pdao;
    @Autowired
    private InformRelationDao irdao;
    @Autowired
    private MailreciverDao mdao;
    @Autowired
    private NotepaperDao ndao;
    @Autowired
    private NotepaperService nservice;

    //	@Value("${img.rootpath}")
    private String rootpath;

    @PostConstruct
    public void UserpanelController() {
        try {
            rootpath = ResourceUtils.getURL("classpath:").getPath().replace("/target/classes/", "/static/image");
            System.out.println(rootpath);

        } catch (IOException e) {
            System.out.println("获取项目路径异常");
        }
    }

    @RequestMapping("userpanel")
    public String index(@SessionAttribute("userId") Long userId, Model model, HttpServletRequest req,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "10") int size) {

        Pageable pa = new PageRequest(page, size);
        User user = null;
        if (!StringUtil.isEmpty((String) req.getAttribute("errormess"))) {
            user = (User) req.getAttribute("users");
            req.setAttribute("errormess", req.getAttribute("errormess"));
        } else if (!StringUtil.isEmpty((String) req.getAttribute("success"))) {
            user = (User) req.getAttribute("users");
            req.setAttribute("success", "fds");
        } else {
            //找到这个用户
            user = udao.findOne(userId);
        }

        //找到部门名称
        String deptname = ddao.findname(user.getDept().getDeptId());

        //找到职位名称
        String positionname = pdao.findById(user.getPosition().getId());

        //找未读通知消息
        List<NoticeUserRelation> noticelist = irdao.findByReadAndUserId(false, user);

        //找未读邮件
        List<Mailreciver> maillist = mdao.findByReadAndDelAndReciverId(false, false, user);

        //找便签
        Page<Notepaper> list = ndao.findByUserIdOrderByCreateTimeDesc(user, pa);

        List<Notepaper> notepaperlist = list.getContent();

        model.addAttribute("user", user);
        model.addAttribute("deptname", deptname);
        model.addAttribute("positionname", positionname);
        model.addAttribute("noticelist", noticelist.size());
        model.addAttribute("maillist", maillist.size());
        model.addAttribute("notepaperlist", notepaperlist);
        model.addAttribute("page", list);
        model.addAttribute("url", "panel");


        return "user/userpanel";
    }

    /**
     * 上下页
     */
    @RequestMapping("panel")
    public String index(@SessionAttribute("userId") Long userId, Model model,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "10") int size) {
        Pageable pa = new PageRequest(page, size);
        User user = udao.findOne(userId);
        //找便签
        Page<Notepaper> list = ndao.findByUserIdOrderByCreateTimeDesc(user, pa);
        List<Notepaper> notepaperlist = list.getContent();
        model.addAttribute("notepaperlist", notepaperlist);
        model.addAttribute("page", list);
        model.addAttribute("url", "panel");
        return "user/panel";
    }

    /**
     * 存便签
     */
    @RequestMapping("writep")
    public String savepaper(Notepaper npaper, @SessionAttribute("userId") Long userId, @RequestParam(value = "concent", required = false) String concent) {
        User user = udao.findOne(userId);
        npaper.setCreateTime(new Date());
        npaper.setUserId(user);
        System.out.println("内容" + npaper.getConcent());
        if (npaper.getTitle() == null || npaper.getTitle().equals(""))
            npaper.setTitle("无标题");
        if (npaper.getConcent() == null || npaper.getConcent().equals(""))
            npaper.setConcent(concent);
        ndao.save(npaper);

        return "redirect:/userpanel";
    }

    /**
     * 删除便签
     */
    @RequestMapping("notepaper")
    public String deletepaper(HttpServletRequest request, @SessionAttribute("userId") Long userId) {
        User user = udao.findOne(userId);
        String paperid = request.getParameter("id");
        Long lpid = Long.parseLong(paperid);
        Notepaper note = ndao.findOne(lpid);
        if (user.getUserId().equals(note.getUserId().getUserId())) {
            nservice.delete(lpid);
        } else {
            System.out.println("权限不匹配，不能删除");
            return "redirect:/notlimit";
        }
        return "redirect:/userpanel";

    }

    /**
     * 修改用户
     *
     * @throws IOException
     * @throws IllegalStateException
     */
    @RequestMapping("saveuser")
    public String saveemp(@RequestParam("filePath") MultipartFile filePath, HttpServletRequest request, @Valid User user,
                          BindingResult br, @SessionAttribute("userId") Long userId) throws IllegalStateException, IOException {
        String imgpath = nservice.upload(filePath);
        User users = udao.findOne(userId);

        //重新set用户
        users.setRealName(user.getRealName());
        users.setUserTel(user.getUserTel());
        users.setEamil(user.getEamil());
        users.setAddress(user.getAddress());
        users.setUserEdu(user.getUserEdu());
        users.setSchool(user.getSchool());
        users.setIdCard(user.getIdCard());
        users.setBank(user.getBank());
        users.setSex(user.getSex());
        users.setThemeSkin(user.getThemeSkin());
        users.setBirth(user.getBirth());
        if (!StringUtil.isEmpty(user.getUserSign())) {
            users.setUserSign(user.getUserSign());
        }
        if (!StringUtil.isEmpty(user.getPassword())) {
            users.setPassword(user.getPassword());
        }
        if (!StringUtil.isEmpty(imgpath)) {
            users.setImgPath(imgpath);

        }

        request.setAttribute("users", users);

        ResultVO res = BindingResultVOUtil.hasErrors(br);
        if (!ResultEnum.SUCCESS.getCode().equals(res.getCode())) {
            List<Object> list = new MapToList<>().mapToList(res.getData());
            request.setAttribute("errormess", list.get(0).toString());

            System.out.println("list错误的实体类信息：" + user);
            System.out.println("list错误详情:" + list);
            System.out.println("list错误第一条:" + list.get(0));
            System.out.println("啊啊啊错误的信息——：" + list.get(0).toString());

        } else {
            udao.save(users);
            request.setAttribute("success", "执行成功！");
        }
        return "forward:/userpanel";

    }

    @RequestMapping("image/**")
    public void image(Model model, HttpServletResponse response, @SessionAttribute("userId") Long userId, HttpServletRequest request)
            throws Exception {
        String projectPath = ClassUtils.getDefaultClassLoader().getResource("").getPath();
        System.out.println(projectPath);
        String startpath = URLDecoder.decode(request.getRequestURI(), "utf-8");

//		String path = startpath.replace("/image", "");
        String path = startpath.substring(7);
//		File f = new File(rootpath, path);
        File f = new File(path);
        System.out.println("startpath" + startpath);
        System.out.println("rootpath" + rootpath);
        System.out.println("path" + path);
        System.out.println("f.getPath" + f.getPath());
        ServletOutputStream sos = response.getOutputStream();
        FileInputStream input = new FileInputStream(f.getPath());
        byte[] data = new byte[(int) f.length()];
        IOUtils.readFully(input, data);
        // 将文件流输出到浏览器
        IOUtils.write(data, sos);
        input.close();
        sos.close();
    }

    @RequestMapping(value = "registUser", method = RequestMethod.POST)
    @ResponseBody
    public JSONObject registUser(User user, @RequestParam("filePath") MultipartFile filePath) throws Exception {
        System.out.println("注册用户：" + user);
        String pinyin = PinyinHelper.convertToPinyinString(user.getUserName(), "", PinyinFormat.WITHOUT_TONE);
        user.setPinyin(pinyin);
        String imgPath = nservice.upload(filePath);
        user.setDept(ddao.findOne(1L));
        user.setPassword("123456");
        JSONObject status = new JSONObject();
        status.put("code", 1);
        status.put("msg", "注册失败");
        if (!StringUtil.isEmpty(imgPath)) {
            user.setImgPath(imgPath);
            JSONObject data = sendInfo(imgPath, user.getIdCard());
            if (Integer.parseInt(data.get("code").toString()) == 0) {
                udao.save(user);
                status.put("code", 0);
                status.put("msg", "验证通过");
            } else {
                status.put("code", 3);
                status.put("msg", "验证不通过");
            }
        } else {
            status.put("code", 2);
            status.put("msg", "上传图片为空");
        }
        return status;
    }

    @RequestMapping(value = "userInfo",method = {RequestMethod.POST})
    @ResponseBody
    public JSONObject userInfo(@RequestParam("idCard") String idCard){
        User user=udao.findOneUserbyIdCard(idCard);
        JSONObject obj=new JSONObject();
        if(Objects.isNull(idCard)||Objects.isNull(user)){
            obj.put("code",1);
            obj.put("msg","无此用户信息");
        }
        else {
            obj.put("code",0);
            obj.put("name",user.getUserName());
            obj.put("msg","成功");
        }
        return obj;
    }

    @PostMapping(value = "/testPost")
    @ResponseBody
    public JSONObject postJson(@RequestParam("file") MultipartFile filePath, @RequestParam("idCard") String idCard) {
        System.out.println("收到文件：" + filePath.getOriginalFilename() + " " + filePath.getSize() + " " + idCard);
        JSONObject obj = new JSONObject();
        obj.put("code", 0);
        obj.put("msg", "success");
        return obj;
    }

    private static JSONObject sendInfo(String imgPath, String idCard) throws IOException {
        final String url = "http://172.16.101.131:5001/register";
        String fileTyle = imgPath.substring(imgPath.lastIndexOf("."), imgPath.length());
        RestTemplate restTemplate = new RestTemplate();
        //设置请求头
        HttpHeaders headers = new HttpHeaders();
        MediaType type = MediaType.parseMediaType("multipart/form-data");
        headers.setContentType(type);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        byte[] byteArray = Files.readAllBytes(Paths.get(new File(imgPath).getAbsolutePath()));
        okhttp3.RequestBody postBodyImage = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", idCard + fileTyle, okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/*jpg"), byteArray))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(postBodyImage)
                .build();
        OkHttpClient client = new OkHttpClient();
        Response response = client.newCall(request).execute();
        JSONObject obj = new JSONObject();
        obj.put("code", 1);
        if (response.body() != null) {
            String data = response.body().string();
            obj = JSON.parseObject(data);
            System.out.print(data);
        }
        return obj;
    }

}
