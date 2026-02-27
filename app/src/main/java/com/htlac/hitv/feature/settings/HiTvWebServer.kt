package com.htlac.hitv.feature.settings

import fi.iki.elonen.NanoHTTPD

/**
 * 回调参数变为两个：(IPTV地址, EPG地址)
 */
class HiTvWebServer(
    port: Int,
    private val onUrlsReceived: (String, String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val params = session.parameters

                // 获取手机端推过来的两个地址
                val pushedIptv = params["m3uUrl"]?.firstOrNull() ?: ""
                val pushedEpg = params["epgUrl"]?.firstOrNull() ?: ""

                // 传回给电视
                onUrlsReceived(pushedIptv, pushedEpg)

                // 返回 JSON 告诉手机端：推送成功，不要刷新页面！
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 新版带有 Ajax 异步提交和 EPG 框的精美页面
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <meta charset="UTF-8">
                <title>HiTV 控制台</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: #f2f2f7; margin: 0; padding: 20px; display: flex; justify-content: center; }
                    .card { background: white; border-radius: 16px; padding: 24px; width: 100%; max-width: 400px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
                    h2 { margin-top: 0; color: #1c1c1e; text-align: center; }
                    label { display: block; margin-top: 16px; margin-bottom: 8px; color: #8e8e93; font-size: 14px; font-weight: bold; }
                    input { width: 100%; padding: 14px; border: 1px solid #e5e5ea; border-radius: 10px; box-sizing: border-box; font-size: 16px; outline: none; transition: border-color 0.2s; }
                    input:focus { border-color: #0A84FF; }
                    button { width: 100%; padding: 16px; margin-top: 24px; background: #0A84FF; color: white; border: none; border-radius: 12px; font-size: 17px; font-weight: 600; cursor: pointer; transition: background 0.2s; }
                    button:active { background: #007aff; }
                    #toast { display: none; position: fixed; top: 20px; left: 50%; transform: translateX(-50%); background: #34c759; color: white; padding: 12px 24px; border-radius: 20px; font-weight: bold; box-shadow: 0 4px 12px rgba(52,199,89,0.3); }
                </style>
            </head>
            <body>
                <div id="toast">推送成功！看一眼电视吧 👀</div>
                <div class="card">
                    <h2>⚙️ HiTV 配置</h2>
                    <form id="configForm">
                        <label>IPTV 订阅链接 (M3U)</label>
                        <input type="text" id="m3uUrl" name="m3uUrl" placeholder="http://..." />
                        
                        <label>节目单链接 (XMLTV/EPG)</label>
                        <input type="text" id="epgUrl" name="epgUrl" placeholder="http://..." />
                        
                        <button type="submit">推送到电视</button>
                    </form>
                </div>

                <script>
                    document.getElementById('configForm').addEventListener('submit', function(e) {
                        e.preventDefault(); // 阻止表单默认的跳转刷新行为
                        
                        const formData = new URLSearchParams(new FormData(this));
                        
                        fetch('/', {
                            method: 'POST',
                            body: formData
                        }).then(response => {
                            if (response.ok) {
                                // 显示顶部绿色提示，2秒后消失
                                const toast = document.getElementById('toast');
                                toast.style.display = 'block';
                                setTimeout(() => { toast.style.display = 'none'; }, 2000);
                            } else {
                                alert("推送失败，请检查网络！");
                            }
                        });
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}