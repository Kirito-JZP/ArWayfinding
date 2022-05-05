package com.main.arwayfinding.logic;

import static com.main.arwayfinding.utility.StaticStringUtils.*;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Define the Logic for invalid input.
 *
 * @author JIA
 * @author Last Modified By JIA
 * @version Revision: 0
 * Date: 2022/5/4 20:58
 */
public class CheckLogic {

    private String errorMessage;

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean checkEmail(String email) {
        Pattern pattern = Pattern.compile("\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*");
        Matcher mc = pattern.matcher(email);
        if (!mc.matches()) {
            this.errorMessage = INCORRECT_EMAIL_FORMAT;
            return true;
        } else {
            return false;
        }
    }

    public boolean isEmpty(String key, String str) {
        if (StringUtils.isEmpty(str)) {
            this.errorMessage = key + EMPTY_STRING;
            return true;
        } else {
            return false;
        }
    }

    public boolean checkLength(int n){
        if(n > 0 && n < 6){
            this.errorMessage = INCORRECT_PASSWORD_FORMAT;
            return true;
        }else {
            return false;
        }
    }


}
