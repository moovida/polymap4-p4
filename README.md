# Polymap4 GIS Client

## Building from source

#### Prerequisites

  * Get and install Java8
  * Get and install [Eclipse for RCP and RAP Developers](http://www.eclipse.org/downloads/packages/eclipse-rcp-and-rap-developers/neon2)
  * (optionally) install GitHub Mylyn support from Marketplace

#### Install target platform

  * Download [polymap4-targetplatform.zip](http://build.mapzone.io/jenkins/job/polymap4-targetplatform/ws/*zip*/polymap4-targetplatform.zip)
  * `unzip polymap4-targetplatform.zip`
  * Eclipse: Preferences->Plugin Developement->Target Platform->Add

#### Get source from GitHub

  * Clone the following repositories from GitHub
    * `git@github.com:Polymap4/polymap4-core.git`
    * `git@github.com:Polymap4/polymap4-rhei.git`
    * `git@github.com:Polymap4/polymap4-rap.git`
    * `git@github.com:Polymap4/polymap4-model.git`
    * `git@github.com:Polymap4/polymap4-p4.git`
  * Import all plugins into workspace
  * **Manually** build workspace (Ctrl-b)
  * **Manually** build workspace, **again** (help Eclipse to find jars downloaded in previous build)
  
#### Compile and Launch

After importing all plugins into the workspace Eclipse automatically downloads all dependencies from maven repositories and compiles the sources. If this did not work properly ...

  * ...
