package com.game.playforge.api.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA路由转发
 * <p>
 * 将非静态资源的GET请求转发到index.html，让React Router在前端处理路由。
 * 正则 {@code [^\\.]*} 排除含点号的路径（如 .js, .css, .html, .ico），避免拦截静态文件。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Controller
public class SpaForwardController {

    @GetMapping("/{path:[^\\.]*}")
    public String forward() {
        return "forward:/index.html";
    }
}
