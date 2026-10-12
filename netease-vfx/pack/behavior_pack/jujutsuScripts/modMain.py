# -*- coding: utf-8 -*-
from mod.common.mod import Mod


@Mod.Binding(name="JujutsuNetease", version="0.2.1")
class JujutsuMod(object):
    @Mod.InitServer()
    def server_init(self):
        import mod.server.extraServerApi as api
        api.RegisterSystem("JujutsuNetease", "Server", "jujutsuScripts.server.JujutsuServer")

    @Mod.InitClient()
    def client_init(self):
        import mod.client.extraClientApi as api
        api.RegisterSystem("JujutsuNetease", "Client", "jujutsuScripts.client.JujutsuClient")
