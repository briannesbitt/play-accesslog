/*
 * Copyright 2011 Brian Nesbitt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package play.modules.accesslog;

import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.mvc.Http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class AccessLogPlugin extends PlayPlugin
{
   //vhost remoteAddress - requestUser [time] "requestUrl" status bytes "referrer" "userAgent" requestTime "POST"
   private static final String FORMAT = "%v %h - %u [%t] \"%r\" %s %b \"%ref\" \"%ua\" %rt \"%post\"";
   private static final String CONFIG_PREFIX = "accesslog.";
   private static final String DEFAULT_PATH = "logs/access.log";
   private static boolean _canLog = false;
   private static PrintWriter _writer;
   private static File _logFile;
   private boolean _shouldLog2Play;
   private boolean _shouldLogPost;

   @Override
   public void onConfigurationRead()
   {
      _shouldLog2Play = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX+"log2play", "false"));
      _shouldLogPost = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX+"logpost", "false"));
      _logFile = new File(Play.configuration.getProperty(CONFIG_PREFIX+"path", DEFAULT_PATH));

      if (!_logFile.isAbsolute())
      {
         _logFile = new File(play.Play.applicationPath, _logFile.getPath());
      }
   }
   @Override
   public void onApplicationStart()
   {
      _canLog = createWriter();
   }
   @Override
   public void onApplicationStop()
   {
      if (_writer != null)
      {
         _writer.close();
      }
   }
   private synchronized boolean createWriter()
   {
      if (!_logFile.exists())
      {
         File parent = _logFile.getParentFile();

         if (parent != null)
         {
            parent.mkdirs();
         }
      }

      try
      {
         _writer = new PrintWriter(new FileOutputStream(_logFile, true), true);
      }
      catch (FileNotFoundException ex)
      {
         Logger.warn("AccessLogPlugin: No accesslog will be used (cannot open file handle) (" + ex.getMessage() + ")");
      }
      return _writer != null;
   }
   @Override
   public void invocationFinally()
   {
      log();
   }
   private synchronized void log()
   {
      if (!_shouldLog2Play && !_canLog)
      {
         return;
      }
      Http.Request request = Http.Request.current();
      Http.Response response = Http.Response.current();

      if (request == null || response == null)
      {
         return;
      }

      long requestProcessingTime = System.currentTimeMillis() - request.date.getTime();

      Http.Header referrer = request.headers.get(HttpHeaders.Names.REFERER.toLowerCase());
      Http.Header userAgent = request.headers.get(HttpHeaders.Names.USER_AGENT.toLowerCase());

      String bytes = "-";
      String status = "-";

      /* It seems as though the Response.current() is only valid when the request is handled by a controller
         Serving static files, static 404's and 500's etc don't populate the same Response.current()
         This prevents us from getting the bytes sent and response status all of the time
       */
      if (request.action != null && response.out.size() > 0)
      {
         bytes = String.valueOf(response.out.size());
         status = response.status.toString();
      }

      String line = FORMAT;
      line = StringUtils.replaceOnce(line, "%v", request.host);
      line = StringUtils.replaceOnce(line, "%h", request.remoteAddress);
      line = StringUtils.replaceOnce(line, "%u", (StringUtils.isEmpty(request.user)) ? "-" : request.user);
      line = StringUtils.replaceOnce(line, "%t", request.date.toString());
      line = StringUtils.replaceOnce(line, "%r", request.url);
      line = StringUtils.replaceOnce(line, "%s", status );
      line = StringUtils.replaceOnce(line, "%b", bytes);
      line = StringUtils.replaceOnce(line, "%ref", (referrer != null) ? referrer.value() : "");
      line = StringUtils.replaceOnce(line, "%ua", (userAgent != null) ? userAgent.value() : "");
      line = StringUtils.replaceOnce(line, "%rt", String.valueOf(requestProcessingTime));

      if (_shouldLogPost && request.method.equals("POST"))
      {
         String body = request.params.get("body");

         if (StringUtils.isNotEmpty(body))
         {
            line = StringUtils.replaceOnce(line, "%post", body);
         }
         else
         {
            // leave quotes in the logged string to show it was an empty POST request
            line = StringUtils.remove(line, "%post");
         }
      }
      else
      {
         line = StringUtils.remove(line, "\"%post\"");
      }

      line = StringUtils.trim(line);

      if (_canLog)
      {
         _writer.println(line);
      }

      if (_shouldLog2Play)
      {
         play.Logger.info(line);
      }
   }
}