/*
 * Application.java
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

package org.rstudio.studio.client.application;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.RunAsyncCallback;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.rstudio.core.client.BrowseCap;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.command.CommandBinder;
import org.rstudio.core.client.command.Handler;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.studio.client.application.ApplicationQuit.QuitContext;
import org.rstudio.studio.client.application.events.*;
import org.rstudio.studio.client.application.model.InvalidSessionInfo;
import org.rstudio.studio.client.application.model.ProductInfo;
import org.rstudio.studio.client.application.model.SessionSerializationAction;
import org.rstudio.studio.client.application.ui.AboutDialog;
import org.rstudio.studio.client.application.ui.RequestLogVisualization;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.SuperDevMode;
import org.rstudio.studio.client.common.satellite.SatelliteManager;
import org.rstudio.studio.client.projects.Projects;
import org.rstudio.studio.client.projects.events.NewProjectEvent;
import org.rstudio.studio.client.projects.events.OpenProjectEvent;
import org.rstudio.studio.client.projects.events.SwitchToProjectEvent;
import org.rstudio.studio.client.server.*;
import org.rstudio.studio.client.workbench.ClientStateUpdater;
import org.rstudio.studio.client.workbench.Workbench;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.events.SessionInitEvent;
import org.rstudio.studio.client.workbench.model.Agreement;
import org.rstudio.studio.client.workbench.model.Session;
import org.rstudio.studio.client.workbench.model.SessionInfo;
import org.rstudio.studio.client.workbench.model.SessionUtils;
import org.rstudio.studio.client.workbench.prefs.model.UIPrefs;
import org.rstudio.studio.client.workbench.views.source.editors.text.themes.AceThemes;

@Singleton
public class Application implements ApplicationEventHandlers
{
   public interface Binder extends CommandBinder<Commands, Application> {}
   
   @Inject
   public Application(ApplicationView view,
                      GlobalDisplay globalDisplay,
                      EventBus events,
                      Binder binder,
                      Commands commands,
                      Server server,
                      Session session,
                      Projects projects,
                      SatelliteManager satelliteManager,
                      ApplicationUncaughtExceptionHandler uncaughtExHandler,
                      ApplicationTutorialApi tutorialApi,
                      MacZoomHandler zoomHandler,
                      Provider<UIPrefs> uiPrefs,
                      Provider<Workbench> workbench,
                      Provider<EventBus> eventBusProvider,
                      Provider<ClientStateUpdater> clientStateUpdater,
                      Provider<ApplicationClientInit> pClientInit,
                      Provider<ApplicationQuit> pApplicationQuit,
                      Provider<ApplicationInterrupt> pApplicationInterrupt,
                      Provider<AceThemes> pAceThemes)
   {
      // save references
      view_ = view ;
      globalDisplay_ = globalDisplay;
      events_ = events;
      session_ = session;
      commands_ = commands;
      satelliteManager_ = satelliteManager;
      clientStateUpdater_ = clientStateUpdater;
      server_ = server;
      uiPrefs_ = uiPrefs;
      workbench_ = workbench;
      eventBusProvider_ = eventBusProvider;
      pClientInit_ = pClientInit;
      pApplicationQuit_ = pApplicationQuit;
      pApplicationInterrupt_ = pApplicationInterrupt;
      pAceThemes_ = pAceThemes;

      // bind to commands
      binder.bind(commands_, this);
      
      // register as main window
      satelliteManager.initialize();
         
      // subscribe to events
      events.addHandler(LogoutRequestedEvent.TYPE, this);
      events.addHandler(UnauthorizedEvent.TYPE, this);
      events.addHandler(ReloadEvent.TYPE, this);
      events.addHandler(QuitEvent.TYPE, this);
      events.addHandler(SuicideEvent.TYPE, this);
      events.addHandler(SessionAbendWarningEvent.TYPE, this);    
      events.addHandler(SessionSerializationEvent.TYPE, this);
      events.addHandler(ServerUnavailableEvent.TYPE, this);
      events.addHandler(InvalidClientVersionEvent.TYPE, this);
      events.addHandler(ServerOfflineEvent.TYPE, this);
      events.addHandler(InvalidSessionEvent.TYPE, this);
      events.addHandler(SwitchToRVersionEvent.TYPE, this);
      
      // register for uncaught exceptions
      uncaughtExHandler.register();
   }
     
   public void go(final RootLayoutPanel rootPanel, 
                  final Command dismissLoadingProgress)
   {
      Widget w = view_.getWidget();
      rootPanel.add(w);
      rootPanel.setWidgetTopBottom(w, 0, Style.Unit.PX, 0, Style.Unit.PX);
      rootPanel.setWidgetLeftRight(w, 0, Style.Unit.PX, 0, Style.Unit.PX);

      // attempt init
      pClientInit_.get().execute(
                              new ServerRequestCallback<SessionInfo>() {

         public void onResponseReceived(final SessionInfo sessionInfo)
         {
            // initialize workbench after verifying agreement
            verifyAgreement(sessionInfo, new Operation() {
               public void execute()
               {
                  // if this is a switch project then wait to dismiss the 
                  // loading progress animation for 10 seconds. typically
                  // this will be enough time to switch projects. if it
                  // isn't then it's nice to reveal whatever progress 
                  // operation or error state is holding up the switch
                  // directly to the user
                  if (ApplicationAction.isSwitchProject())
                  {
                     new Timer() {
                        @Override
                        public void run()
                        {
                           dismissLoadingProgress.execute();
                        }   
                     }.schedule(10000);  
                  }
                  else
                  {
                     dismissLoadingProgress.execute();
                  }
                  
                  session_.setSessionInfo(sessionInfo);
                        
                  // initialize workbench
                  initializeWorkbench();
               }
            }); 
         }

         public void onError(ServerError error)
         {
            Debug.logError(error);
            dismissLoadingProgress.execute();

            globalDisplay_.showErrorMessage("RStudio Initialization Error",
                                            error.getUserMessage());
         }
      }) ;
   }  
   
   
   @Handler
   public void onShowToolbar()
   {
      setToolbarPref(true);
   }
   
   @Handler
   public void onHideToolbar()
   {
      setToolbarPref(false);
   }
   
   @Handler
   public void onToggleToolbar()
   {
      setToolbarPref(!view_.isToolbarShowing());
   }
   
   @Handler
   void onShowAboutDialog()
   {
      server_.getProductInfo(new ServerRequestCallback<ProductInfo>()
      {
         @Override
         public void onResponseReceived(ProductInfo info)
         {
            AboutDialog about = new AboutDialog(info);
            about.showModal();
         }
         @Override
         public void onError(ServerError error)
         {
         }
      });
   }
   
   public void onUnauthorized(UnauthorizedEvent event)
   {
      navigateToSignIn();
   }   
   
   public void onServerOffline(ServerOfflineEvent event)
   {
      cleanupWorkbench();
      view_.showApplicationOffline();
   }
    
   public void onLogoutRequested(LogoutRequestedEvent event)
   {
      navigateWindowTo("auth-sign-out");
   }
   
   @Handler
   public void onHelpUsingRStudio()
   {
      String customDocsURL = session_.getSessionInfo().docsURL();
      if (customDocsURL.length() > 0)
         globalDisplay_.openWindow(customDocsURL);
      else
         globalDisplay_.openRStudioLink("docs");
   }
   
   private void showAgreement()
   {
      globalDisplay_.openWindow(server_.getApplicationURL("agreement"));
   }
   
   @Handler
   public void onRstudioSupport()
   {
      globalDisplay_.openRStudioLink("support");
   }
   
   @Handler
   public void onRstudioAgreement()
   {
      showAgreement();
   }
   
   @Handler
   public void onUpdateCredentials()
   {
      server_.updateCredentials();
   }

   @Handler
   public void onRaiseException() {
      throw new RuntimeException("foo");
   }

   @Handler
   public final native void onRaiseException2() /*-{
      $wnd.welfkjweg();
   }-*/;
   
   @Handler
   public void onShowRequestLog()
   {
      GWT.runAsync(new RunAsyncCallback()
      {
         public void onFailure(Throwable reason)
         {
            Window.alert(reason.toString());
         }

         public void onSuccess()
         {
            final RequestLogVisualization viz = new RequestLogVisualization();
            final RootLayoutPanel root = RootLayoutPanel.get();
            root.add(viz);
            root.setWidgetTopBottom(viz, 10, Unit.PX, 10, Unit.PX);
            root.setWidgetLeftRight(viz, 10, Unit.PX, 10, Unit.PX);
            viz.addCloseHandler(new CloseHandler<RequestLogVisualization>()
            {
               public void onClose(CloseEvent<RequestLogVisualization> event)
               {
                  root.remove(viz);
               }
            });
         }
      });
   }

   @Handler
   public void onLogFocusedElement()
   {
      Element el = DomUtils.getActiveElement();
      DomUtils.dump(el, "Focused Element: ");
   }

   @Handler
   public void onRefreshSuperDevMode()
   {
      SuperDevMode.reload();
   }
   
   public void onSessionSerialization(SessionSerializationEvent event)
   {
      switch(event.getAction().getType())
      {
      case SessionSerializationAction.LOAD_DEFAULT_WORKSPACE:
         view_.showSerializationProgress(
                         "Loading workspace" + getSuffix(event), 
                         false, // non-modal, appears to user as std latency
                         500,   // willing to show progress earlier since
                                // this will always be at workbench startup
                         0);    // no timeout
         break;
      case SessionSerializationAction.SAVE_DEFAULT_WORKSPACE:
         view_.showSerializationProgress(
                          "Saving workspace image" + getSuffix(event), 
                          true, // modal, inputs will fall dead anyway
                          0,    // show immediately
                          0);   // no timeout
         break;
      case SessionSerializationAction.SUSPEND_SESSION:
         view_.showSerializationProgress(
                          "Backing up R session...",
                          true,    // modal, inputs will fall dead anyway
                          0,       // show immediately
                          60000);  // timeout after 60 seconds. this is done
                                   // in case the user suspends or loses
                                   // connectivity during the backup (in which
                                   // case the 'completed' event dies with
                                   // server and is never received by the client
         break;
      case SessionSerializationAction.RESUME_SESSION:
         view_.showSerializationProgress(
                          "Resuming R session...",
                          false, // non-modal, appears to user as std latency
                          2000,  // don't show this for reasonable restore time
                                 // (happens inline while using a running
                                 // workbench so be more conservative)
                          0);    // no timeout
         break;
      case SessionSerializationAction.COMPLETED:
         view_.hideSerializationProgress();
         break;
      }
   }
   
   private String getSuffix(SessionSerializationEvent event)
   {
      SessionSerializationAction action = event.getAction();
      String targetPath = action.getTargetPath();
      if (targetPath != null)
      {
         String verb = " from ";
         if (action.getType() == SessionSerializationAction.SAVE_DEFAULT_WORKSPACE)
            verb = " to ";
         return verb + targetPath + "...";
      }
      else
      {
         return "...";
      }
   }
   
   public void onServerUnavailable(ServerUnavailableEvent event)
   {
      view_.hideSerializationProgress();
   }
   
   @Override
   public void onSwitchToRVersion(final SwitchToRVersionEvent event)
   {
      final ApplicationQuit applicaitonQuit = pApplicationQuit_.get();
      applicaitonQuit.prepareForQuit("Switch R Version", 
                                             new QuitContext() {
         public void onReadyToQuit(boolean saveChanges)
         {
            // see if we have a project (otherwise switch to "None")
            String project = session_.getSessionInfo().getActiveProjectFile();
            if (project == null)
               project = Projects.NONE;
            
            // do the quit
            applicaitonQuit.performQuit(saveChanges, 
                                        project, 
                                        event.getRVersionSpec());
         }   
      });
   }

   public void onReload(ReloadEvent event)
   {
      cleanupWorkbench();
      
      reloadWindowWithDelay(false);
   }
   
   public void onQuit(QuitEvent event)
   {
      cleanupWorkbench();  
      
      // only show the quit state in server mode (desktop mode has its
      // own handling triggered to process exit)
      if (!Desktop.isDesktop())
      {
         // if we are switching projects then reload after a delay (to allow
         // the R session to fully exit on the server)
         if (event.getSwitchProjects())
         {
            String nextSessionUrl = event.getNextSessionUrl();
            if (!StringUtil.isNullOrEmpty(nextSessionUrl))
            {
               // forward any query string parameters (e.g. the edit_published
               // parameter might follow an action=switch_project)
               String query = ApplicationAction.getQueryStringWithoutAction();
               if (query.length() > 0)
                  nextSessionUrl = nextSessionUrl + "?" + query;
               
               navigateWindowWithDelay(nextSessionUrl);
            }
            else
            {
               reloadWindowWithDelay(true);
            }
         }
         else 
         { 
            if (session_.getSessionInfo().getMultiSession())
            {
               view_.showApplicationMultiSessionQuit();
            }
            else
            {
               view_.showApplicationQuit();
            }
            
            // attempt to close the window if this is a quit
            // action (may or may not be able to depending on 
            // how it was created)
            if (ApplicationAction.isQuit())
            {
               try
               {
                  WindowEx.get().close();
               }
               catch(Exception ex)
               {
               }
            }
            else if (session_.getSessionInfo().getShowUserHomePage())
            {
               navigateWindowWithDelay(
                     session_.getSessionInfo().getUserHomePageUrl());
            }
         }
      }
   }
   
   private void reloadWindowWithDelay(final boolean baseUrlOnly)
   {
      new Timer() {
         @Override
         public void run()
         { 
            if (baseUrlOnly)
               Window.Location.replace(GWT.getHostPageBaseURL());
            else
               Window.Location.reload();
         }
      }.schedule(100);
   }
   
   private void navigateWindowWithDelay(final String url)
   {
      new Timer() {
         @Override
         public void run()
         { 
            Window.Location.replace(url);
         }
      }.schedule(100);
   }
   
   public void onSuicide(SuicideEvent event)
   { 
      cleanupWorkbench();
      view_.showApplicationSuicide(event.getMessage());
   }
   
   public void onClientDisconnected(ClientDisconnectedEvent event)
   {
      cleanupWorkbench();
      view_.showApplicationDisconnected();
   }
   
   public void onInvalidClientVersion(InvalidClientVersionEvent event)
   {
      cleanupWorkbench();
      view_.showApplicationUpdateRequired();
   }
   

   public void onInvalidSession(InvalidSessionEvent event)
   {
      // calculate the url without the scope
      InvalidSessionInfo info = event.getInfo();
      String baseURL = GWT.getHostPageBaseURL();
      String scopePath = info.getScopePath();
      int loc = baseURL.indexOf(scopePath);
      if (loc != -1)
         baseURL = baseURL.substring(0, loc) + "/";

      if (info.getScopeState() == InvalidSessionInfo.ScopeMissingProject)
      {
         baseURL += "projectnotfound.htm";
      }
      else
      {
         // add the scope info to the query string
         baseURL += "?project="
               + URL.encodeQueryString(info.getSessionProject()) + "&id="
               + URL.encodeQueryString(info.getSessionProjectId());
      }
      navigateWindowWithDelay(baseURL);
   }

   public void onSessionAbendWarning(SessionAbendWarningEvent event)
   {
      view_.showSessionAbendWarning();
   }
   
   private void verifyAgreement(SessionInfo sessionInfo,
                              final Operation verifiedOperation)
   {
      // get the agreement (if any)
      final Agreement agreement = sessionInfo.pendingAgreement();
      
      // if there is an agreement then prompt user for agreement (otherwise just
      // execute the verifiedOperation immediately)
      if (agreement != null)
      {
         // append updated to the title if necessary
         String title = agreement.getTitle();
         if (agreement.getUpdated())
            title += " (Updated)";
         
         view_.showApplicationAgreement(
            
            // title and contents   
            title,
            agreement.getContents(),
             
            // bail to sign in page if the user doesn't confirm
            new Operation()
            {
               public void execute()
               {
                  if (Desktop.isDesktop())
                  {
                     Desktop.getFrame().setPendingQuit(
                                       DesktopFrame.PENDING_QUIT_AND_EXIT);
                     server_.quitSession(false,
                                         null,
                                         null,
                                         GWT.getHostPageBaseURL(),
                                         new SimpleRequestCallback<Boolean>());
                  }
                  else
                     navigateToSignIn();
               }
            },
        
            // user confirmed
            new Operation() {
               public void execute()
               {
                  // call verified operation
                  verifiedOperation.execute();
                  
                  // record agreement on server
                  server_.acceptAgreement(agreement, 
                                          new VoidServerRequestCallback());
               } 
            }
            
         );
         
      }
      else
      {
         // no agreement pending
         verifiedOperation.execute();
      }
   }
   
   private void navigateWindowTo(String relativeUrl)
   {
      navigateWindowTo(relativeUrl, true);
   }
   
   private void navigateWindowTo(String relativeUrl, boolean includeContext)
   {
      cleanupWorkbench();
    
      // ensure there is no session context if requested
      String url = includeContext ? 
            GWT.getHostPageBaseURL() :
            ApplicationUtils.getHostPageBaseURLWithoutContext(true);
            
      // add relative URL
      url += relativeUrl;
     
      // navigate window
      Window.Location.replace(url);
   }
   
   private void initializeWorkbench()
   {
      pAceThemes_.get();

      // subscribe to ClientDisconnected event (wait to do this until here
      // because there were spurious ClientDisconnected events occuring
      // after a session interrupt sequence. we couldn't figure out why,
      // and since this is a temporary hack why not add another temporary
      // hack to go with it here :-)
      // TOOD: move this back tot he constructor after we revise the
      // interrupt hack(s)
      events_.addHandler(ClientDisconnectedEvent.TYPE, this); 
      
      // create workbench
      Workbench wb = workbench_.get();
      eventBusProvider_.get().fireEvent(new SessionInitEvent()) ;

      // disable commands
      SessionInfo sessionInfo = session_.getSessionInfo();
      if (!sessionInfo.getAllowShell())
      {
         commands_.showShellDialog().remove();
      }
      if (!sessionInfo.getAllowPackageInstallation())
      {
         commands_.installPackage().remove();
         commands_.updatePackages().remove();
      }
      if (!sessionInfo.getAllowVcs())
      {
         commands_.versionControlProjectSetup().remove();
      }
      if (!sessionInfo.getAllowFileDownloads())
      {
         commands_.exportFiles().remove();
      }
      
      // disable external publishing if requested
      if (!SessionUtils.showExternalPublishUi(session_, uiPrefs_.get()))
      {
         commands_.publishHTML().remove();
      } 
      
      // hide the agreement menu item if we don't have one
      if (!session_.getSessionInfo().hasAgreement())
         commands_.rstudioAgreement().setVisible(false);
           
      // remove knit params if they aren't supported
      if (!sessionInfo.getKnitParamsAvailable())
         commands_.knitWithParameters().remove();
         
      // show workbench
      view_.showWorkbenchView(wb.getMainView().asWidget());
      
      // hide zoom actual size everywhere but cocoa desktop
      if (!BrowseCap.isCocoaDesktop())
      {
         commands_.zoomActualSize().remove();
      }
      
      // hide zoom in and zoom out in web mode
      if (!Desktop.isDesktop())
      {
         commands_.zoomIn().remove();
         commands_.zoomOut().remove();
      }
      
      // show new session when appropriate
      if (!Desktop.isDesktop())
      {
         if (sessionInfo.getMultiSession())
            commands_.newSession().setMenuLabel("New Session...");
         else
            commands_.newSession().remove();
      }
      
      // toolbar (must be after call to showWorkbenchView because
      // showing the toolbar repositions the workbench view widget)
      showToolbar( uiPrefs_.get().toolbarVisible().getValue());
      
      // sync to changes in the toolbar visibility state
      uiPrefs_.get().toolbarVisible().addValueChangeHandler(
                                          new ValueChangeHandler<Boolean>() {
         @Override
         public void onValueChange(ValueChangeEvent<Boolean> event)
         {
            showToolbar(event.getValue());
         }
      });
      
      clientStateUpdaterInstance_ = clientStateUpdater_.get();
      
      // initiate action if requested. do this after a delay 
      // so that the source database has time to load
      // before we interrogate it for unsaved documents
      if (ApplicationAction.hasAction())
      {
         new Timer() {
            @Override
            public void run() {
               if (ApplicationAction.isQuit())
               {
                  commands_.quitSession().execute();
               }
               else if (ApplicationAction.isNewProject())
               {
                  ApplicationAction.removeActionFromUrl();
                  events_.fireEvent(new NewProjectEvent(true, false));
               }
               else if (ApplicationAction.isOpenProject())
               {
                  ApplicationAction.removeActionFromUrl();
                  events_.fireEvent(new OpenProjectEvent(true, false));
               }
               else if (ApplicationAction.isSwitchProject())
               {
                  handleSwitchProjectAction();
               }
            }
         }.schedule(500); 
      }
   }
   
   private void handleSwitchProjectAction()
   { 
      String projectId = ApplicationAction.getId();
      if (projectId.length() > 0)
      {
         server_.getProjectFilePath(
            projectId, 
            new ServerRequestCallback<String>() {

               @Override
               public void onResponseReceived(String projectFilePath)
               {
                  if (projectFilePath.length() > 0)
                  {
                     events_.fireEvent(
                           new SwitchToProjectEvent(projectFilePath, true));
                  }
               }
               @Override
               public void onError(ServerError error)
               {
                  Debug.logError(error);
               }
         
            });
      } 
   }
 
   
   private void setToolbarPref(boolean showToolbar)
   {
      uiPrefs_.get().toolbarVisible().setGlobalValue(showToolbar);
      uiPrefs_.get().writeUIPrefs();
   }
   
   private void showToolbar(boolean showToolbar)
   {
      // show or hide the toolbar
      view_.showToolbar(showToolbar);
         
      // manage commands
      commands_.showToolbar().setVisible(!showToolbar);
      commands_.hideToolbar().setVisible(showToolbar);
   }
      
   private void cleanupWorkbench()
   {
      server_.disconnect();
      
      satelliteManager_.closeAllSatellites();
      
      if (clientStateUpdaterInstance_ != null)
      {
         clientStateUpdaterInstance_.suspend();
         clientStateUpdaterInstance_ = null;
      }
   }
   
   private void navigateToSignIn()
   {
      navigateWindowTo("auth-sign-in");
   }
   
   
   private final ApplicationView view_ ;
   private final GlobalDisplay globalDisplay_ ;
   private final EventBus events_;
   private final Session session_;
   private final Commands commands_;
   private final SatelliteManager satelliteManager_;
   private final Provider<ClientStateUpdater> clientStateUpdater_;
   private final Server server_;
   private final Provider<UIPrefs> uiPrefs_;
   private final Provider<Workbench> workbench_;
   private final Provider<EventBus> eventBusProvider_;
   private final Provider<ApplicationClientInit> pClientInit_;
   private final Provider<ApplicationQuit> pApplicationQuit_;
   private final Provider<ApplicationInterrupt> pApplicationInterrupt_;
   private final Provider<AceThemes> pAceThemes_;

   private ClientStateUpdater clientStateUpdaterInstance_;
}
