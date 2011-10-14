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
import org.apache.log4j.Appender;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import play.Invoker;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.mvc.Http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.logging.Level;

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
      _shouldLog2Play = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX+"log2play", "true"));
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

      long requestProcessingTime = System.currentTimeMillis() - request.date.getTime();

      Http.Header contentLength = response.headers.get(HttpHeaders.Names.CONTENT_LENGTH.toLowerCase());
      Http.Header referrer = request.headers.get(HttpHeaders.Names.REFERER.toLowerCase());
      Http.Header userAgent = request.headers.get(HttpHeaders.Names.USER_AGENT.toLowerCase());

      String line = FORMAT;
      line = StringUtils.replaceOnce(line, "%v", request.host);
      line = StringUtils.replaceOnce(line, "%h", request.remoteAddress);
      line = StringUtils.replaceOnce(line, "%u", (StringUtils.isEmpty(request.user)) ? "-" : request.user);
      line = StringUtils.replaceOnce(line, "%t", request.date.toString());
      line = StringUtils.replaceOnce(line, "%r", request.url);
      line = StringUtils.replaceOnce(line, "%s", response.status.toString());
      line = StringUtils.replaceOnce(line, "%b", (contentLength != null) ? contentLength.value() : "-");
      line = StringUtils.replaceOnce(line, "%ref", (referrer != null) ? referrer.value() : "");
      line = StringUtils.replaceOnce(line, "%ua", (userAgent != null) ? userAgent.value() : "");
      line = StringUtils.replaceOnce(line, "%rt", String.valueOf(requestProcessingTime));

      if (_shouldLogPost)
      {
         line = StringUtils.replaceOnce(line, "%post", request.params.get("body"));
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