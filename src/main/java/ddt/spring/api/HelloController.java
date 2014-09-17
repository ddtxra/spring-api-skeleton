package ddt.spring.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * A controller exposing secured and unsecured methods (defined on auth0-security-context.xml)
 * 
 * @author Daniel Teixeira
 */
@Controller
public class HelloController {

	@RequestMapping(value = "/hello", method = { RequestMethod.GET })
	@ResponseBody
	public Hello hello() {
		return new Hello();
	}

}
