package com.couchface.sports.CollegeBasketballStatsCrawler;

import java.io.IOException;

import com.couchface.sports.basketballstats.entity.Game;
import com.couchface.sports.basketballstats.service.BasketballStatsService;
import com.couchface.sports.basketballstats.service.impl.MensCollegeService;

public class App 
{
    public static void main( String[] args )
    {
        GameParser parser = new GameParser(2, 20, 5);
        try {
			Game game = parser.parseGame((int) 3789415);
			BasketballStatsService service = new MensCollegeService();
			System.out.println("Done!");
		} catch (IOException e) {
			System.out.println("Whoops, not working yet.");
		}
    }
}
