package org.example.scan;

import org.example.imported.LocalDateConfiguration;
import org.example.imported.ZonedDateConfiguration;
import org.example.annotation.ComponentScan;
import org.example.annotation.Import;

@ComponentScan
@Import({ LocalDateConfiguration.class, ZonedDateConfiguration.class })
public class ScanApplication {

}
