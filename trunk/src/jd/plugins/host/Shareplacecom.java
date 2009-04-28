//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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

package jd.plugins.host;

import java.io.IOException;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.http.Encoding;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginForHost;

public class Shareplacecom extends PluginForHost {

    private String url;

    public Shareplacecom(PluginWrapper wrapper) {
        super(wrapper);
    }

    //@Override
    public String getAGBLink() {
        return "http://shareplace.com/rules.php";
    }

    //@Override
    public boolean getFileInformation(DownloadLink downloadLink) throws IOException {
        String url = downloadLink.getDownloadURL();
        setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(url);
        if (br.getRedirectLocation() == null) {
            downloadLink.setName(Encoding.htmlDecode(br.getRegex(Pattern.compile("File name: </b>(.*?)<b>", Pattern.CASE_INSENSITIVE)).getMatch(0)));
            String filesize = null;
            if ((filesize = br.getRegex("File size: </b>(.*)MB<b>").getMatch(0)) != null) {
                downloadLink.setDownloadSize((int) Math.round(Double.parseDouble(filesize)) * 1024 * 1024);
            } else if ((filesize = br.getRegex("File size: </b>(.*)KB<b>").getMatch(0)) != null) {
                downloadLink.setDownloadSize((int) Math.round(Double.parseDouble(filesize)) * 1024);
            } else if ((filesize = br.getRegex("File size: </b>(.*)byte<b>").getMatch(0)) != null) {
                downloadLink.setDownloadSize((int) Math.round(Double.parseDouble(filesize)));
            }
            return true;
        } else
            return false;

    }

    //@Override
    public String getVersion() {

        return getVersion("$Revision$");
    }

    //@Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        LinkStatus linkStatus = downloadLink.getLinkStatus();

        /* Nochmals das File überprüfen */
        if (!getFileInformation(downloadLink)) {
            linkStatus.addStatus(LinkStatus.ERROR_FILE_NOT_FOUND);
            return;
        }
        /* Link holen */
        url = Encoding.htmlDecode(br.getRegex(Pattern.compile("document.location=\"(.*?)\";", Pattern.CASE_INSENSITIVE)).getMatch(0));

        /* Zwangswarten, 20seks */
        sleep(20000, downloadLink);
        br.setDebug(true);
        br.setFollowRedirects(true);
        dl = br.openDownload(downloadLink, url);
        if (dl.getConnection().isContentDisposition()) {
            /* Workaround für fehlerhaften Filename Header */
            String name = Plugin.getFileNameFormHeader(dl.getConnection());
            if (name != null) downloadLink.setFinalFileName(Encoding.urlDecode(name, false));
        }
        dl.startDownload();
    }

    //@Override
    public int getMaxSimultanFreeDownloadNum() {
        return 20;
    }

    //@Override
    public void reset() {
    }

    //@Override
    public void resetPluginGlobals() {
    }

    //@Override
    public void reset_downloadlink(DownloadLink link) {
        // TODO Auto-generated method stub

    }
}
