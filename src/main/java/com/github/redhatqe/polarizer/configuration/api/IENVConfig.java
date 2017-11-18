package com.github.redhatqe.polarizer.configuration.api;

import com.github.redhatqe.polarizer.configuration.api.IConfigurator;

public interface IENVConfig extends IConfigurator<String> {
    void setupDefaultVars();
}
