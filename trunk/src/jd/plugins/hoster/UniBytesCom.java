//    jDownloader - Downloadmanager
//    Copyright (C) 2011  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.IOException;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "unibytes.com" }, urls = { "http://(www\\.)?unibytes\\.com/[a-zA-Z0-9\\-\\.\\_ ]{11}B" }, flags = { 2 })
public class UniBytesCom extends PluginForHost {

    private static final String CAPTCHATEXT      = "captcha\\.jpg";

    private static final String FATALSERVERERROR = "<u>The requested resource \\(\\) is not available\\.</u>";

    private static final String MAINPAGE         = "http://www.unibytes.com/";

    public UniBytesCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.unibytes.com/vippay");
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        try {
            login(account);
        } catch (PluginException e) {
            account.setValid(false);
            return ai;
        }
        ai.setUnlimitedTraffic();
        ai.setStatus("Premium User");
        return ai;
    }

    @Override
    public String getAGBLink() {
        return "http://www.unibytes.com/page/terms";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        if (br.containsHTML(FATALSERVERERROR)) throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Fatal server error");
        String addedLink = downloadLink.getDownloadURL();
        br.setFollowRedirects(false);
        br.postPage(addedLink, "step=timer&referer=&ad=");
        String dllink = br.getRedirectLocation();
        if (dllink == null || !dllink.contains("fdload/")) {
            dllink = null;
            if (br.containsHTML("(showNotUniqueIP\\(\\);|>Somebody else is already downloading using your IP-address<)")) throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Too many simultan downloads", 10 * 60 * 1000l);
            int iwait = 60;
            String regexedTime = br.getRegex("id=\"slowRest\">(\\d+)</").getMatch(0);
            if (regexedTime == null) regexedTime = br.getRegex("var timerRest = (\\d+);").getMatch(0);
            if (regexedTime != null) iwait = Integer.parseInt(regexedTime);
            String ipBlockedTime = br.getRegex("guestDownloadDelayValue\">(\\d+)</span>").getMatch(0);
            if (ipBlockedTime == null) ipBlockedTime = br.getRegex("guestDownloadDelay\\((\\d+)\\);").getMatch(0);
            if (ipBlockedTime != null) throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, Integer.parseInt(ipBlockedTime) * 60 * 1001l);
            String s = br.getRegex("name=\"s\" value=\"(.*?)\"").getMatch(0);
            if (s == null) {
                logger.warning("s1 equals null!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            sleep(iwait * 1001l, downloadLink);
            br.postPage(downloadLink.getDownloadURL(), "step=next&s=" + s + "&referer=" + addedLink);
            s = br.getRegex("name=\"s\" value=\"(.*?)\"").getMatch(0);
            if (s == null) {
                logger.warning("s2 equals null!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.postPage(downloadLink.getDownloadURL(), "step=captcha&s=" + s + "&referer=" + addedLink);
            if (br.containsHTML(CAPTCHATEXT)) {
                logger.info("Captcha found");
                for (int i = 0; i <= 5; i++) {
                    String code = getCaptchaCode("http://www.unibytes.com/captcha.jpg", downloadLink);
                    String post = "s=" + s + "&referer=" + addedLink + "&step=last&captcha=" + code;
                    br.postPage(downloadLink.getDownloadURL(), post);
                    if (!br.containsHTML(CAPTCHATEXT)) break;
                }
                if (br.containsHTML(CAPTCHATEXT)) throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            } else {
                logger.info("Captcha not found");
            }
            dllink = br.getRegex("\"(http://st\\d+\\.unibytes\\.com/fdload/file.*?)\"").getMatch(0);
            if (dllink == null) dllink = br.getRegex("style=\"width: 650px; margin: 40px auto; text-align: center; font-size: 2em;\"><a href=\"(.*?)\"").getMatch(0);
        }
        if (dllink == null) {
            logger.warning("dllink equals null!");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML(FATALSERVERERROR)) throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Fatal server error");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        requestFileInformation(link);
        login(account);
        br.setFollowRedirects(false);
        br.getPage(link.getDownloadURL());
        Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa != null) {
            AccountInfo ai = new AccountInfo();
            String expireDate = br.getRegex("Ваш VIP-аккаунт действителен до ([0-9\\.]+)\\.<br/><br/><a").getMatch(0);
            if (expireDate != null) {
                ai.setValidUntil(TimeFormatter.getMilliSeconds(expireDate, "dd.MM.yyyy", null));
            } else {
                ai.setExpired(true);
            }
            account.setAccountInfo(ai);
            if (ai.isExpired()) throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
        }
        String dllink = br.getRegex("style=\"text-align:center; padding:50px 0;\"><a href=\"(http.*?)\"").getMatch(0);
        dllink = null;
        if (dllink == null) dllink = br.getRegex("\"(http://st\\d+\\.unibytes\\.com/download/file.*?\\?referer=.*?)\"").getMatch(0);
        if (dllink == null) {
            logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    // do not add @Override here to keep 0.* compatibility
    public boolean hasCaptcha() {
        return true;
    }

    private void login(Account account) throws Exception {
        this.setBrowserExclusive();
        br.setCookie(MAINPAGE, "lang", "en");
        br.postPage(MAINPAGE, "lb_login=" + Encoding.urlEncode(account.getUser()) + "&lb_password=" + Encoding.urlEncode(account.getPass()) + "&lb_remember=true");
        if (br.getCookie(MAINPAGE, "hash") == null) throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        // Use the english language
        br.setCookie(MAINPAGE, "lang", "en");
        br.getPage(link.getDownloadURL());
        if (br.containsHTML("<p>File not found or removed</p>")) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        if (br.containsHTML(FATALSERVERERROR)) return AvailableStatus.UNCHECKABLE;
        String filename = br.getRegex("id=\"fileName\" style=\"[^\"\\']+\">(.*?)</span>").getMatch(0);
        String filesize = br.getRegex("\\((\\d+\\.\\d+ [A-Za-z]+)\\)</h3><script>").getMatch(0);
        if (filesize == null) filesize = br.getRegex("</span>[\t\n\r ]+\\((.*?)\\)</h3><script>").getMatch(0);
        if (filename == null || filesize == null) {
            // Leave this in
            logger.warning("Fatal error happened in the availableCheck...");
            logger.warning("Filename = " + filename);
            logger.warning("Filesize = " + filesize);
            logger.warning(br.toString());
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(filename.trim());
        link.setDownloadSize(SizeFormatter.getSize(filesize));
        return AvailableStatus.TRUE;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}